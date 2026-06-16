package chaoxing.autosign.service;

import chaoxing.autosign.entity.*;
import chaoxing.autosign.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignTaskService {

    private final TaskStatusRepository taskStatusRepository;
    private final SignRecordRepository signRecordRepository;
    private final SignLogRepository signLogRepository;
    private final SignConfigRepository signConfigRepository;
    private final SignTimeWindowRepository timeWindowRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SIGN_LOCK_PREFIX = "sign:lock:";
    private static final String TASK_RUNNING_PREFIX = "task:running:";
    private static final String WINDOW_DONE_PREFIX = "sign:done:";
    private static final Duration SIGN_LOCK_TTL = Duration.ofHours(1);

    /** 特殊课程名：无条件执行签到（跳过时间窗口和预签到检查） */
    private static final String SPECIAL_UNCONDITIONAL_COURSE = "课程教学示例";

    // 超星签到相关 API
    private static final String ACTIVE_LIST_URL =
            "https://mobilelearn.chaoxing.com/v2/apis/active/student/activelist";
    private static final String PRE_SIGN_URL =
            "https://mobilelearn.chaoxing.com/newsign/preSign";
    private static final String SIGN_AJAX_URL =
            "https://mobilelearn.chaoxing.com/pptSign/stuSignajax";

    /**
     * 启动签到任务
     */
    @Transactional
    public void startTask(Long userId) {
        TaskStatus taskStatus = taskStatusRepository.findByUserId(userId)
                .orElseGet(() -> TaskStatus.builder().userId(userId).build());

        taskStatus.setStatus("running");
        taskStatus.setLastRunAt(LocalDateTime.now());
        taskStatusRepository.save(taskStatus);

        // Redis 标记运行
        redisTemplate.opsForValue().set(TASK_RUNNING_PREFIX + userId, "1");

        log.info("签到任务启动: userId={}", userId);
    }

    /**
     * 停止签到任务
     */
    @Transactional
    public void stopTask(Long userId) {
        TaskStatus taskStatus = taskStatusRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("任务状态不存在"));

        taskStatus.setStatus("stopped");
        taskStatusRepository.save(taskStatus);

        redisTemplate.delete(TASK_RUNNING_PREFIX + userId);

        log.info("签到任务停止: userId={}", userId);
    }

    /**
     * 获取任务状态
     */
    public Map<String, Object> getTaskStatus(Long userId) {
        TaskStatus taskStatus = taskStatusRepository.findByUserId(userId)
                .orElse(TaskStatus.builder().userId(userId).status("stopped").build());

        return Map.of(
                "status", taskStatus.getStatus(),
                "lastRunAt", taskStatus.getLastRunAt() != null ? taskStatus.getLastRunAt().toString() : "",
                "nextRunAt", taskStatus.getNextRunAt() != null ? taskStatus.getNextRunAt().toString() : ""
        );
    }

    /**
     * 执行签到流程（由 Scheduler 定时调用）
     * 按用户 → 签到配置 → 时间窗口 → 活动列表 的层级逐一处理。
     * 关键优化：签到成功后标记当前时间窗口为 "已签"，后续轮询直接跳过 getActiveList 调用，避免防火墙拦截。
     */
    @SuppressWarnings("unchecked")
    public void executeSignLoop() {
        List<TaskStatus> runningTasks = taskStatusRepository.findAll()
                .stream()
                .filter(t -> "running".equals(t.getStatus()))
                .toList();

        Set<Long> refreshedUsers = new HashSet<>();

        for (TaskStatus task : runningTasks) {
            Long userId = task.getUserId();
            try {
                String runningKey = TASK_RUNNING_PREFIX + userId;
                if (!"1".equals(redisTemplate.opsForValue().get(runningKey))) {
                    continue;
                }

                String cookies = getOrRefreshCookies(userId, refreshedUsers);
                if (cookies == null) {
                    userService.addSignLog(userId, "warn", "Cookie 已过期且刷新失败，跳过签到");
                    continue;
                }

                List<Course> courses = courseRepository.findByUserId(userId);
                List<SignConfig> configs = signConfigRepository.findByUserId(userId);

                if (configs.isEmpty()) {
                    continue;
                }

                // 批量加载所有配置的时间窗口
                List<Long> configIds = configs.stream().map(SignConfig::getId).toList();
                List<SignTimeWindow> allWindows = timeWindowRepository.findByConfigIdIn(configIds);
                Map<Long, List<SignTimeWindow>> windowMap = new HashMap<>();
                for (SignTimeWindow w : allWindows) {
                    windowMap.computeIfAbsent(w.getConfigId(), k -> new ArrayList<>()).add(w);
                }

                for (SignConfig config : configs) {
                    try {
                        String courseName = config.getCourseName();

                        Course matchedCourse = courses.stream()
                                .filter(c -> courseName.equals(c.getCourseName()))
                                .findFirst().orElse(null);

                        String courseId = matchedCourse != null ? matchedCourse.getCourseId() : "0";
                        String classId = matchedCourse != null ? matchedCourse.getClassId() : "0";

                        boolean isSpecialCourse = SPECIAL_UNCONDITIONAL_COURSE.equals(courseName);

                        // ===== 多时间窗口检查 =====
                        List<SignTimeWindow> windows = windowMap.getOrDefault(config.getId(),
                                buildLegacyWindows(config));
                        SignTimeWindow currentWindow = findCurrentWindow(windows);

                        if (!isSpecialCourse && currentWindow == null) {
                            continue; // 不在任何签到时间窗口内
                        }

                        // ===== 防重复轮询：该时间窗口已签到成功 → 不再调 getActiveList =====
                        if (currentWindow != null && isWindowDone(userId, config.getId(), currentWindow)) {
                            continue;
                        }

                        // 获取活动列表
                        List<Map<String, Object>> activeList = getActiveList(cookies, courseId, classId);

                        if (activeList == null) {
                            String newCookies = refreshIfNeeded(userId, refreshedUsers);
                            if (newCookies != null) {
                                cookies = newCookies;
                                activeList = getActiveList(cookies, courseId, classId);
                            }
                        }
                        if (activeList == null || activeList.isEmpty()) {
                            continue;
                        }

                        // 遍历活动
                        for (Map<String, Object> activity : activeList) {
                            String activeId = String.valueOf(activity.get("id"));
                            String activityName = String.valueOf(activity.getOrDefault("nameOne", "未知活动"));
                            String status = String.valueOf(activity.getOrDefault("status", ""));

                            if ("2".equals(status) || "3".equals(status)) {
                                continue;
                            }

                            if (signRecordRepository.existsByUserIdAndActiveId(userId, activeId)) {
                                continue;
                            }

                            String lockKey = SIGN_LOCK_PREFIX + userId + ":" + activeId;
                            Boolean locked = redisTemplate.opsForValue()
                                    .setIfAbsent(lockKey, "1", SIGN_LOCK_TTL);
                            if (locked == null || !locked) {
                                continue;
                            }

                            Optional<SignConfig> configOpt = Optional.of(config);

                            // 特殊课程 → 跳过预签到
                            if (isSpecialCourse) {
                                log.info("[无条件签到] 课程={}, activity={}, activeId={}", courseName, activityName, activeId);
                                Map<String, Object> signResult = executeSign(cookies, activeId, courseName, configOpt);
                                if (signResult == null) {
                                    String newCookies = refreshIfNeeded(userId, refreshedUsers);
                                    if (newCookies != null) {
                                        cookies = newCookies;
                                        signResult = executeSign(cookies, activeId, courseName, configOpt);
                                    }
                                }
                                handleSignResult(userId, courseName, activeId, activity, signResult,
                                        configOpt, lockKey, task, currentWindow);
                                continue;
                            }

                            // 正常流程：预签到 → 签到
                            Map<String, Object> preResult = preSign(cookies, activeId, courseName);
                            if (preResult == null) {
                                String newCookies = refreshIfNeeded(userId, refreshedUsers);
                                if (newCookies != null) {
                                    cookies = newCookies;
                                    preResult = preSign(cookies, activeId, courseName);
                                }
                                if (preResult == null) {
                                    redisTemplate.delete(lockKey);
                                    continue;
                                }
                            }

                            Map<String, Object> signResult = executeSign(cookies, activeId, courseName, configOpt);
                            if (signResult == null) {
                                String newCookies = refreshIfNeeded(userId, refreshedUsers);
                                if (newCookies != null) {
                                    cookies = newCookies;
                                    signResult = executeSign(cookies, activeId, courseName, configOpt);
                                }
                            }
                            handleSignResult(userId, courseName, activeId, activity, signResult,
                                    configOpt, lockKey, task, currentWindow);
                        }
                    } catch (Exception e) {
                        log.error("处理签到配置异常: userId={}, courseName={}", userId, config.getCourseName(), e);
                    }
                }

            } catch (Exception e) {
                log.error("签到执行异常: userId={}", task.getUserId(), e);
                userService.addSignLog(task.getUserId(), "error", "签到异常: " + e.getMessage());
            }
        }
    }

    // ==================== 时间窗口工具方法 ====================

    /**
     * 在当前时间命中某个时间窗口时返回该窗口，否则返回 null。
     * 时间比较使用 HH:mm 字符串比较（如 "14:30" >= "14:30" 且 < "14:33"）。
     */
    private SignTimeWindow findCurrentWindow(List<SignTimeWindow> windows) {
        if (windows == null || windows.isEmpty()) return null;

        LocalTime now = LocalTime.now();
        String nowStr = String.format("%02d:%02d", now.getHour(), now.getMinute());

        for (SignTimeWindow w : windows) {
            String start = w.getStartTime();
            String end = w.getEndTime();
            if (start == null || end == null) continue;
            // 当前时间 >= 开始时间 且 < 结束时间
            if (nowStr.compareTo(start) >= 0 && nowStr.compareTo(end) < 0) {
                return w;
            }
        }
        return null;
    }

    /** 从旧字段构建时间窗口（兼容 DB 无 SignTimeWindow 数据的情况） */
    private List<SignTimeWindow> buildLegacyWindows(SignConfig config) {
        if (config.getSignStartTime() != null && !config.getSignStartTime().isEmpty()
                && config.getSignEndTime() != null && !config.getSignEndTime().isEmpty()) {
            return List.of(SignTimeWindow.builder()
                    .configId(config.getId())
                    .startTime(config.getSignStartTime())
                    .endTime(config.getSignEndTime())
                    .build());
        }
        return List.of();
    }

    /** 标记某个时间窗口已签到成功，TTL 到当天结束 */
    private void markWindowDone(Long userId, Long configId, SignTimeWindow window) {
        if (window == null) return;
        String key = buildWindowDoneKey(userId, configId, window);
        long secondsUntilMidnight = ChronoUnit.SECONDS.between(
                LocalDateTime.now(),
                LocalDate.now().plusDays(1).atStartOfDay()
        );
        redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(Math.max(secondsUntilMidnight, 60)));
        log.debug("时间窗口已标记为完成: userId={}, configId={}, window={}-{}",
                userId, configId, window.getStartTime(), window.getEndTime());
    }

    /** 检查某个时间窗口是否已完成签到 */
    private boolean isWindowDone(Long userId, Long configId, SignTimeWindow window) {
        if (window == null) return false;
        String key = buildWindowDoneKey(userId, configId, window);
        return "1".equals(redisTemplate.opsForValue().get(key));
    }

    private String buildWindowDoneKey(Long userId, Long configId, SignTimeWindow window) {
        String today = LocalDate.now().toString(); // yyyy-MM-dd
        return WINDOW_DONE_PREFIX + userId + ":" + configId + ":" + today + ":" + window.getStartTime();
    }

    // ==================== Cookie 管理 ====================

    private String getOrRefreshCookies(Long userId, Set<Long> refreshedUsers) {
        String cookies = authService.getCookieFromCache(userId);
        if (cookies == null) {
            return refreshIfNeeded(userId, refreshedUsers);
        }
        return cookies;
    }

    private String refreshIfNeeded(Long userId, Set<Long> refreshedUsers) {
        if (refreshedUsers.contains(userId)) {
            return null;
        }
        String newCookies = authService.refreshCookie(userId);
        if (newCookies != null) {
            refreshedUsers.add(userId);
        }
        return newCookies;
    }

    /** 统一处理签到结果 */
    private void handleSignResult(Long userId, String courseName, String activeId,
                                   Map<String, Object> activity, Map<String, Object> signResult,
                                   Optional<SignConfig> configOpt, String lockKey, TaskStatus task,
                                   SignTimeWindow currentWindow) {
        if (signResult != null) {
            saveSignRecord(userId, courseName, activeId, activity, signResult);
            updateTaskRunTime(task);
            // 签到成功 → 标记当前时间窗口已完成，后续轮询不再调取活动列表
            if (currentWindow != null) {
                markWindowDone(userId, configOpt.map(SignConfig::getId).orElse(0L), currentWindow);
            }
        } else {
            userService.addSignLog(userId, "fail",
                    "签到失败: " + courseName + " - " + activity.getOrDefault("nameOne", "未知活动"));
            redisTemplate.delete(lockKey);
        }
    }

    // ==================== 签到 API 方法 ====================

    /** 保存签到记录 */
    private void saveSignRecord(Long userId, String courseName, String activeId,
                                 Map<String, Object> activity, Map<String, Object> signResult) {
        String activityName = String.valueOf(activity.getOrDefault("nameOne", "未知活动"));

        SignRecord record = SignRecord.builder()
                .userId(userId)
                .courseId(extractCourseId(activeId))
                .courseName(courseName)
                .activeId(activeId)
                .signType(activityName)
                .signTime(LocalDateTime.now())
                .status("success")
                .resultMsg(String.valueOf(signResult.getOrDefault("msg", "签到成功")))
                .build();
        signRecordRepository.save(record);

        userService.addSignLog(userId, "success",
                "签到成功: " + courseName + " - " + activityName);
    }

    private void updateTaskRunTime(TaskStatus task) {
        task.setLastRunAt(LocalDateTime.now());
        task.setNextRunAt(LocalDateTime.now().plusSeconds(30));
        taskStatusRepository.save(task);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getActiveList(String cookies, String courseId, String classId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Cookie", cookies);
            headers.set("User-Agent", "Mozilla/5.0");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            String url = ACTIVE_LIST_URL + "?fid=0&courseId=" + courseId + "&classId=" + classId
                    + "&showNotStartedActive=0&_=" + System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class
            );

            String respBody = response.getBody();
            if (respBody != null) {
                if (respBody.trim().startsWith("<")) {
                    log.warn("获取活动列表返回了HTML页面(Cookie可能已过期)");
                    return null;
                }
                Map<String, Object> body = objectMapper.readValue(respBody,
                        new TypeReference<Map<String, Object>>() {});
                if (body.containsKey("data")) {
                    Map<String, Object> data = (Map<String, Object>) body.get("data");
                    return (List<Map<String, Object>>) data.getOrDefault("activeList", List.of());
                }
            }
        } catch (Exception e) {
            log.error("获取活动列表失败: {}", e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> preSign(String cookies, String activeId, String courseName) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Cookie", cookies);
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

            String body = "courseId=&classId=&activePrimaryId=" + activeId + "&general=1&sys=1&ls=1&appType=15&tid=&uid=&ut=s";
            HttpEntity<String> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    PRE_SIGN_URL, HttpMethod.POST, entity, String.class
            );

            String respBody = response.getBody();
            if (respBody != null) {
                if (respBody.trim().startsWith("<")) {
                    log.warn("预签到返回了HTML页面: activeId={}", activeId);
                    return null;
                }
                return objectMapper.readValue(respBody, new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {
            log.error("预签到失败: activeId={}, err={}", activeId, e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeSign(String cookies, String activeId,
                                             String courseName, Optional<SignConfig> configOpt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Cookie", cookies);
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

            StringBuilder bodyBuilder = new StringBuilder();
            bodyBuilder.append("activeId=").append(activeId);
            bodyBuilder.append("&uid=&clientip=&latitude=-1&longitude=-1&appType=15&fid=0");

            if (configOpt.isPresent()) {
                SignConfig config = configOpt.get();
                if (config.getSignCode() != null && !config.getSignCode().isEmpty()) {
                    bodyBuilder.append("&signCode=").append(config.getSignCode());
                }
                if (config.getAddress() != null && !config.getAddress().isEmpty()) {
                    bodyBuilder.append("&address=").append(java.net.URLEncoder.encode(config.getAddress(), "UTF-8"));
                }
                if (config.getLatitude() != null && config.getLongitude() != null) {
                    bodyBuilder.append("&latitude=").append(config.getLatitude());
                    bodyBuilder.append("&longitude=").append(config.getLongitude());
                }
            }

            HttpEntity<String> entity = new HttpEntity<>(bodyBuilder.toString(), headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    SIGN_AJAX_URL, HttpMethod.POST, entity, String.class
            );

            String respBody = response.getBody();
            if (respBody != null) {
                if (respBody.trim().startsWith("<")) {
                    log.warn("执行签到返回了HTML页面: activeId={}", activeId);
                    return null;
                }
                if (respBody.contains("success") || respBody.contains("签到成功") || respBody.contains("您已签到过了")) {
                    return Map.of("msg", respBody.trim());
                }
                try {
                    return objectMapper.readValue(respBody, new TypeReference<Map<String, Object>>() {});
                } catch (Exception jsonEx) {
                    log.warn("签到响应非标准JSON，视为成功: activeId={}, body={}", activeId,
                            respBody.length() > 100 ? respBody.substring(0, 100) : respBody);
                    return Map.of("msg", respBody.trim());
                }
            }
        } catch (Exception e) {
            log.error("执行签到失败: activeId={}, courseName={}, err={}", activeId, courseName, e.getMessage());
        }
        return null;
    }

    private String extractCourseId(String activeId) {
        return activeId.length() > 5 ? activeId.substring(0, 5) : activeId;
    }
}
