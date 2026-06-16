package chaoxing.autosign.service;

import chaoxing.autosign.entity.Course;
import chaoxing.autosign.entity.User;
import chaoxing.autosign.repository.CourseRepository;
import chaoxing.autosign.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String CHAOXING_BASE = "https://mooc2-ans.chaoxing.com";
    private static final String INTERACTION_URL =
            CHAOXING_BASE + "/visit/interaction";
    private static final String COURSE_LIST_URL =
            CHAOXING_BASE + "/mooc2-ans/visit/courselistdata";
    private static final String PROGRESS_URL =
            "https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/stu-job-info";
    private static final String COURSE_CACHE_PREFIX = "course:list:";
    private static final Duration COURSE_CACHE_TTL = Duration.ofMinutes(10);
    private static final Pattern DATE_RANGE_PATTERN =
            Pattern.compile("(\\d{4}-\\d{2}-\\d{2})[～~](\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern COURSE_TIME_PATTERN =
            Pattern.compile("开课时间[：:]\\s*(\\d{4}-\\d{2}-\\d{2})[～~](\\d{4}-\\d{2}-\\d{2})");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String SCHEDULE_URL =
            "https://kb.chaoxing.com/pc/curriculum/getMyLessons";

    /**
     * 获取用户的课程列表
     * 模拟 PHP class.js 的逻辑：
     * 1. 调 courselistdata 获取 HTML
     * 2. 用 Jsoup 解析 .learnCourse 元素
     * 3. 调 stu-job-info 获取进度
     * 4. 合并返回
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getCourseList(Long userId) {
        // 检查缓存
        String cacheKey = COURSE_CACHE_PREFIX + userId;
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            try {
                return objectMapper.readValue(cachedJson,
                        new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e) {
                log.warn("[CourseService] 缓存解析失败: {}", e.getMessage());
            }
        }

        String cookies = authService.getCookieFromCache(userId);
        if (cookies == null) {
            log.warn("[CourseService] Cookie为空，返回空列表");
            return List.of();
        }

        try {
            // ===== 步骤1: 发现用户所有文件夹 =====
            Map<String, String> folderMap = discoverFolders(cookies);
            log.info("[CourseService] 发现 {} 个文件夹", folderMap.size());

            Set<String> visitedFolders = new HashSet<>();
            List<Map<String, Object>> courseList = new ArrayList<>();

            // 先取根目录课程
            fetchCoursesFromFolder(cookies, "0", courseList, visitedFolders, null);

            // 再遍历每个文件夹
            for (Map.Entry<String, String> entry : folderMap.entrySet()) {
                fetchCoursesFromFolder(cookies, entry.getKey(), courseList,
                        visitedFolders, entry.getValue());
            }

            if (courseList.isEmpty()) {
                log.warn("[CourseService] HTML中未找到 .learnCourse 元素");
                return List.of();
            }
            log.info("[CourseService] HTML解析到 {} 门课程（含子目录）", courseList.size());

            // ===== 步骤2: 调 stu-job-info 获取进度 =====
            Map<String, Map<String, Object>> progressMap = fetchProgress(cookies, courseList);

            // ===== 步骤3: 合并进度 + 补全 userId =====
            for (Map<String, Object> course : courseList) {
                course.put("userId", userId);
                String clazzId = (String) course.getOrDefault("classId", "");
                Map<String, Object> progress = progressMap.get(clazzId);
                if (progress != null) {
                    course.put("progressDone", progress.getOrDefault("finished", 0));
                    course.put("progressTotal", progress.getOrDefault("total", 0));
                } else {
                    course.put("progressDone", 0);
                    course.put("progressTotal", 0);
                }
                course.putIfAbsent("dayOfWeek", 0);
                course.putIfAbsent("id", null);
            }

            // 缓存
            redisTemplate.opsForValue().set(cacheKey,
                    objectMapper.writeValueAsString(courseList), COURSE_CACHE_TTL);

            log.info("[CourseService] 返回 {} 门课程", courseList.size());
            return courseList;

        } catch (Exception e) {
            log.error("[CourseService] 获取课程列表异常: {} - {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 获取用户的文件夹列表
     * 从 interaction 页面解析文件夹，返回列表供前端展示
     */
    public List<Map<String, String>> getFolderList(Long userId) {
        String cookies = authService.getCookieFromCache(userId);
        if (cookies == null) {
            log.warn("[CourseService] Cookie为空，无法获取文件夹");
            return List.of();
        }

        Map<String, String> folderMap = discoverFolders(cookies);
        List<Map<String, String>> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : folderMap.entrySet()) {
            Map<String, String> folder = new LinkedHashMap<>();
            folder.put("id", entry.getKey());
            folder.put("name", entry.getValue());
            result.add(folder);
        }
        return result;
    }

    /**
     * 递归获取指定文件夹下的课程
     * 对应 JS 中 ajaxGetCourseList() 的 POST 请求
     * @param parentFolderName 父文件夹名（根目录为 null）
     */
    private void fetchCoursesFromFolder(String cookies, String folderId,
                                        List<Map<String, Object>> courseList,
                                        Set<String> visitedFolders,
                                        String parentFolderName) {
        if (!visitedFolders.add(folderId)) return;

        String respBody;
        try {
            HttpHeaders headers = buildFormHeaders(cookies);
            headers.set("Referer", CHAOXING_BASE + "/visit/interaction");

            // 模拟 JS: $.ajax({url: "/mooc2-ans/visit/courselistdata", type: "post", data: {...}})
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("courseType", "1");
            formData.add("courseFolderId", folderId);
            formData.add("query", "");
            formData.add("pageHeader", "-1");
            formData.add("single", "0");
            formData.add("superstarClass", "0");
            formData.add("isFirefly", "0");

            HttpEntity<MultiValueMap<String, String>> entity =
                    new HttpEntity<>(formData, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    COURSE_LIST_URL, HttpMethod.POST, entity, String.class);
            respBody = response.getBody();
        } catch (Exception e) {
            log.warn("[CourseService] 获取目录 folderId={} 失败: {}", folderId, e.getMessage());
            return;
        }

        if (respBody == null || respBody.isBlank()) return;

        Document doc = Jsoup.parse(respBody);

        // 解析课程，标记所属文件夹
        Elements courseElements = doc.select(".learnCourse");
        for (Element el : courseElements) {
            try {
                Map<String, Object> course = parseSingleCourse(el);
                if (parentFolderName != null && !parentFolderName.isEmpty()) {
                    course.put("folderName", parentFolderName);
                }
                courseList.add(course);
            } catch (Exception e) {
                log.warn("[CourseService] 解析课程元素失败: {}", e.getMessage());
            }
        }

        // 解析文件夹（ID + 名称）
        Map<String, String> childFolderMap = extractFolders(doc);

        // 递归进入子文件夹
        for (Map.Entry<String, String> entry : childFolderMap.entrySet()) {
            if (!entry.getKey().equals(folderId)) {
                fetchCoursesFromFolder(cookies, entry.getKey(), courseList,
                        visitedFolders, entry.getValue());
            }
        }
    }

    /**
     * 从 interaction 页面 HTML 中解析用户的所有课程文件夹
     * 文件夹直接嵌入在 <ul class="file-list" id="fileList"> 中，
     * 每个 <li fileid="9057879" id="folder_9057879"> 包含 <h3 class="file-name">文件夹名</h3>
     */
    private Map<String, String> discoverFolders(String cookies) {
        Map<String, String> result = new LinkedHashMap<>();

        try {
            HttpHeaders headers = buildHeaders(cookies);
            headers.set("Referer", "https://i.chaoxing.com/");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    INTERACTION_URL,
                    HttpMethod.GET, entity, String.class);

            String body = response.getBody();
            if (body == null || body.isBlank()) return result;

            Document doc = Jsoup.parse(body);

            // 解析 <ul class="file-list" id="fileList"> 下的 <li fileid="xxx">
            Element fileList = doc.selectFirst("#fileList");
            if (fileList == null) {
                log.info("[CourseService] interaction 页面中未找到 #fileList");
                return result;
            }

            for (Element li : fileList.select("li[fileid]")) {
                String fileId = li.attr("fileid");
                Element nameEl = li.selectFirst(".file-name");
                String name = nameEl != null ? nameEl.text().trim() : "";
                if (!fileId.isEmpty() && !name.isEmpty()) {
                    result.put(fileId, name);
                }
            }

        } catch (Exception e) {
            log.warn("[CourseService] 从 interaction 页面获取文件夹失败: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 从 courselistdata HTML 中提取子文件夹（ID -> 名称）
     * 文件夹在 HTML 中以 .catalogItem 或 .folder-item 等元素出现
     */
    private Map<String, String> extractFolders(Document doc) {
        Map<String, String> result = new LinkedHashMap<>();

        // 常见的文件夹元素选择器
        for (String sel : new String[]{
                ".catalogItem",
                ".folder-item",
                ".catalog-item",
                "a[data-folder-id]",
                "a[onclick*=\"courseFolder\"]"}) {
            for (Element el : doc.select(sel)) {
                // 提取文件夹 ID
                String id = el.attr("data-folder-id");
                if (id.isEmpty()) id = el.attr("data-id");
                if (id.isEmpty()) {
                    // 从 onclick 中提取
                    Matcher m = Pattern.compile("courseFolderId[=']+(\\d+)").matcher(el.attr("onclick"));
                    if (m.find()) id = m.group(1);
                }
                // 提取文件夹名称
                String name = el.ownText().trim();
                if (name.isEmpty()) name = el.text().trim();
                // 去除多余的图标文字
                name = name.replaceAll("^[▶▼▲\\s]+", "").trim();

                if (!id.isEmpty() && !name.isEmpty()) {
                    result.put(id, name);
                }
            }
            if (!result.isEmpty()) break;
        }

        return result;
    }

    /**
     * 解析单个 .learnCourse 元素
     * 对应 PHP class.js 中 DOM 解析逻辑
     */
    private Map<String, Object> parseSingleCourse(Element el) {
        Map<String, Object> course = new LinkedHashMap<>();

        // 隐藏字段
        course.put("courseId", val(el, ".courseId"));
        course.put("classId", val(el, ".clazzId"));
        course.put("curPersonId", val(el, ".curPersonId"));
        course.put("role", val(el, ".role"));

        // 封面图 - 处理懒加载 + 相对路径补全
        course.put("coverUrl", extractCoverUrl(el));

        // 课程名
        Element nameEl = el.selectFirst(".course-name");
        course.put("courseName", nameEl != null ? nameEl.text().trim() : "");

        // 教师（.color3）
        Element teacherEl = el.selectFirst(".color3");
        course.put("teacherName", teacherEl != null ? teacherEl.text().trim() : "");

        // 学校（.color2）
        Element schoolEl = el.selectFirst(".color2");
        course.put("schoolName", schoolEl != null ? schoolEl.text().trim() : "");

        // 日期范围：优先匹配"开课时间：2026-03-10～2028-03-10"格式
        Elements allPs = el.select(".course-info p");
        String dateText = "";
        for (Element p : allPs) {
            String text = p.text().trim();
            if (text.contains("开课时间") || text.contains("开课日期")) {
                dateText = text;
                break;
            }
        }
        // 兜底：取最后一个 p
        if (dateText.isEmpty() && !allPs.isEmpty()) {
            dateText = allPs.last().text().trim();
        }
        parseDateRange(course, dateText);

        // 状态：判断是否已结束
        boolean isEnded = el.selectFirst(".not-open-tip") != null;
        course.put("status", isEnded ? 0 : 1);

        return course;
    }

    /**
     * 提取封面图 URL：懒加载属性优先，相对路径补全为绝对路径
     */
    private String extractCoverUrl(Element parent) {
        Element coverArea = parent.selectFirst(".course-cover");
        if (coverArea == null) return "";

        // 1) 优先 img 标签 + 懒加载属性
        Element coverImg = coverArea.selectFirst("img");
        if (coverImg != null) {
            String url = extractImgUrl(coverImg);
            if (!url.isEmpty()) return url;
        }

        // 2) 检查 div 的 style background-image
        String style = coverArea.attr("style");
        Matcher bgMatcher = Pattern.compile("url\\(['\"]?([^)'\"]+)['\"]?\\)").matcher(style);
        if (bgMatcher.find()) {
            String url = bgMatcher.group(1);
            return resolveUrl(url);
        }

        // 3) 检查 .course-cover 内部所有 img 标签
        Elements allImgs = coverArea.select("img");
        for (Element img : allImgs) {
            String url = extractImgUrl(img);
            if (!url.isEmpty()) return url;
        }

        return "";
    }

    /** 从 img 标签提取图片 URL（优先级：data-src > data-original > data-echo > src） */
    private String extractImgUrl(Element img) {
        // 超星常用懒加载属性：data-src, data-original, data-echo, data-lazy-src
        for (String attr : new String[]{"data-src", "data-original", "data-echo", "data-lazy-src", "src"}) {
            String val = img.attr(attr);
            if (!val.isBlank() && !val.equals("about:blank") && !val.startsWith("data:image")) {
                return resolveUrl(val);
            }
        }
        return "";
    }

    /** 相对路径补全为绝对路径，http 升级为 https */
    private String resolveUrl(String url) {
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return CHAOXING_BASE + url;
        if (url.startsWith("http://")) return url.replaceFirst("^http://", "https://");
        return url;
    }

    /**
     * 解析日期范围：兼容"2024-09-01～2025-01-15"和"开课时间：2024-09-01～2025-01-15"
     */
    private void parseDateRange(Map<String, Object> course, String dateText) {
        // 先尝试带"开课时间"前缀的格式
        Matcher m = COURSE_TIME_PATTERN.matcher(dateText);
        if (!m.find()) {
            // 再尝试纯日期格式
            m = DATE_RANGE_PATTERN.matcher(dateText);
        }
        if (m.find()) {
            course.put("startDate", m.group(1));
            course.put("endDate", m.group(2));
        } else {
            course.put("startDate", "");
            course.put("endDate", "");
        }
    }

    /**
     * 调 stu-job-info 获取课程进度
     * 对应 PHP class.js 的 fetchCourseProgress()
     */
    private Map<String, Map<String, Object>> fetchProgress(
            String cookies, List<Map<String, Object>> courseList) {

        Map<String, Map<String, Object>> result = new LinkedHashMap<>();

        // 构建 clazzPersonStr: clazzId_curPersonId,clazzId_curPersonId,...
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> c : courseList) {
            String clazzId = (String) c.getOrDefault("classId", "");
            String curPersonId = (String) c.getOrDefault("curPersonId", "");
            if (!clazzId.isEmpty() && !curPersonId.isEmpty()) {
                if (sb.length() > 0) sb.append(",");
                sb.append(clazzId).append("_").append(curPersonId);
            }
        }

        if (sb.length() == 0) return result;

        try {
            HttpHeaders headers = buildHeaders(cookies);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    PROGRESS_URL + "?clazzPersonStr=" + sb.toString(),
                    HttpMethod.GET, entity, String.class);

            String body = response.getBody();
            if (body != null && !body.trim().startsWith("<")) {
                Map<String, Object> json = objectMapper.readValue(body,
                        new TypeReference<Map<String, Object>>() {});
                List<Map<String, Object>> jobArray = (List<Map<String, Object>>)
                        json.getOrDefault("jobArray", List.of());

                for (Map<String, Object> job : jobArray) {
                    Map<String, Object> progress = new LinkedHashMap<>();
                    progress.put("rate", job.getOrDefault("jobRate", 0));
                    progress.put("finished", job.getOrDefault("jobFinishCount", 0));
                    progress.put("total", job.getOrDefault("jobCount", 0));

                    Object clazzIdObj = job.get("clazzId");
                    String clazzIdKey = clazzIdObj != null ? clazzIdObj.toString() : "";
                    if (!clazzIdKey.isEmpty()) {
                        result.put(clazzIdKey, progress);
                    }
                }
            }

            log.info("[CourseService] 获取到 {} 门课程的进度", result.size());
        } catch (Exception e) {
            log.warn("[CourseService] 获取课程进度失败: {}", e.getMessage());
        }

        return result;
    }

    private HttpHeaders buildHeaders(String cookies) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Cookie", cookies);
        headers.set("User-Agent", "Mozilla/5.0");
        return headers;
    }

    private HttpHeaders buildFormHeaders(String cookies) {
        HttpHeaders headers = buildHeaders(cookies);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return headers;
    }

    private String val(Element parent, String selector) {
        Element el = parent.selectFirst(selector);
        return el != null ? el.val() : "";
    }

    /**
     * 将从超星获取的课程数据持久化到数据库
     * 策略：按 userId + courseId 做 upsert
     */
    @Transactional
    public List<Course> saveCoursesFromRaw(Long userId, List<Map<String, Object>> rawList) {
        List<Course> result = new ArrayList<>();

        for (Map<String, Object> raw : rawList) {
            try {
                String courseId = (String) raw.getOrDefault("courseId", "");
                if (courseId.isEmpty()) continue;

                // 查找已存在的课程（upsert）
                Course course = courseRepository.findByUserIdAndCourseId(userId, courseId)
                        .orElse(Course.builder()
                                .userId(userId)
                                .courseId(courseId)
                                .build());

                course.setClassId((String) raw.getOrDefault("classId", ""));
                course.setCourseName((String) raw.getOrDefault("courseName", ""));
                course.setTeacherName((String) raw.getOrDefault("teacherName", ""));
                course.setSchoolName((String) raw.getOrDefault("schoolName", ""));
                course.setFolderName((String) raw.getOrDefault("folderName", ""));
                course.setCoverUrl((String) raw.getOrDefault("coverUrl", ""));

                // 日期转换
                String startDateStr = (String) raw.getOrDefault("startDate", "");
                String endDateStr = (String) raw.getOrDefault("endDate", "");
                if (!startDateStr.isEmpty()) {
                    course.setStartDate(LocalDate.parse(startDateStr, DATE_FMT));
                }
                if (!endDateStr.isEmpty()) {
                    course.setEndDate(LocalDate.parse(endDateStr, DATE_FMT));
                }

                // 状态和进度
                Object statusObj = raw.get("status");
                course.setStatus(statusObj instanceof Integer ? (Integer) statusObj : 1);

                Object doneObj = raw.get("progressDone");
                course.setProgressDone(doneObj instanceof Integer ? (Integer) doneObj : 0);

                Object totalObj = raw.get("progressTotal");
                course.setProgressTotal(totalObj instanceof Integer ? (Integer) totalObj : 0);

                Object dowObj = raw.get("dayOfWeek");
                course.setDayOfWeek(dowObj instanceof Integer ? (Integer) dowObj : 0);

                result.add(courseRepository.save(course));
            } catch (Exception e) {
                log.warn("[CourseService] 保存课程失败: {} - {}", raw.get("courseName"), e.getMessage());
            }
        }

        return result;
    }

    /**
     * 获取当天需要签到的课程
     */
    public List<Course> getTodayCourses(Long userId, int dayOfWeek) {
        return courseRepository.findByUserIdAndDayOfWeek(userId, dayOfWeek);
    }

    /**
     * 获取课程表数据（从 kb.chaoxing.com）
     * 对应 PHP course.js 的 fetchLessonData()
     *
     * @param userId 用户ID
     * @param week   null=默认当前周, 具体数字=指定周
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getSchedule(Long userId, Integer week) {
        String cookies = authService.getCookieFromCache(userId);
        if (cookies == null) {
            log.warn("[Schedule] Cookie为空");
            return Map.of();
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("[Schedule] 用户不存在, userId={}", userId);
            return Map.of();
        }

        try {
            HttpHeaders headers = buildHeaders(cookies);
            headers.set("Referer", "https://kb.chaoxing.com/");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            long curTime = System.currentTimeMillis();
            String url = SCHEDULE_URL + "?curTime=" + curTime + "&user=" + user.getUsername();

            if (week != null) {
                url += "&week=" + week + "&curTime=" + curTime;
            }

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            String body = response.getBody();
            if (body == null || body.trim().startsWith("<")) {
                log.warn("[Schedule] 响应为空或HTML");
                return Map.of();
            }

            Map<String, Object> json = objectMapper.readValue(body,
                    new TypeReference<Map<String, Object>>() {});
            Object data = json.get("data");
            if (data instanceof Map) {
                return (Map<String, Object>) data;
            }

            return Map.of();
        } catch (Exception e) {
            log.error("[Schedule] 获取课表失败: {}", e.getMessage());
            return Map.of();
        }
    }
}
