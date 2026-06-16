package chaoxing.autosign.service;

import chaoxing.autosign.entity.SignLog;
import chaoxing.autosign.entity.User;
import chaoxing.autosign.repository.SignLogRepository;
import chaoxing.autosign.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final SignLogRepository signLogRepository;
    private final AuthService authService;
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String USER_INFO_URL = "https://sso.chaoxing.com/apis/login/userLogin4Uname.do";
    private static final String USER_INFO_CACHE_PREFIX = "user:info:";
    private static final Duration USER_INFO_TTL = Duration.ofMinutes(30);

    /**
     * 获取用户信息（优先从 DB 判断完整性，再查缓存）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getUserInfo(Long userId) {
        String cacheKey = USER_INFO_CACHE_PREFIX + userId;
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));

        // 如果 DB 已有完整信息，优先用缓存
        if (user.getUid() != null && user.getSchool() != null && !user.getSchool().isEmpty()) {
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null) {
                try {
                    Map<String, Object> cached = objectMapper.readValue(cachedJson,
                            new TypeReference<Map<String, Object>>() {});
                    // 确保缓存中的学校也不为空
                    Object cachedSchool = cached.get("school");
                    if (cachedSchool != null && !cachedSchool.toString().isEmpty()) {
                        return cached;
                    }
                } catch (Exception e) {
                    log.warn("解析用户信息缓存失败", e);
                }
                // 缓存无效，删除之
                redisTemplate.delete(cacheKey);
            }

            // DB 有数据但缓存失效，从 DB 重建
            Map<String, Object> info = buildInfoMap(user);
            cacheInfo(cacheKey, info);
            return info;
        }

        // DB 数据不完整，从超星获取用户信息
        try {
            String cookies = authService.getCookieFromCache(userId);
            if (cookies == null) {
                log.warn("Cookie 已过期，无法从超星获取用户信息");
                // 兜底返回 DB 中已有的部分信息
                return buildInfoMap(user);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("Cookie", cookies);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    USER_INFO_URL,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            String body = response.getBody();
            if (body != null) {
                updateUserInfoFromResponse(user, body);
                userRepository.save(user);
            }
        } catch (Exception e) {
            log.error("获取超星用户信息失败: userId={}", userId, e);
        }

        Map<String, Object> info = buildInfoMap(user);
        // 只有当学校非空时才缓存（避免缓存不完整数据）
        if (user.getSchool() != null && !user.getSchool().isEmpty()) {
            cacheInfo(cacheKey, info);
        }
        return info;
    }

    private Map<String, Object> buildInfoMap(User user) {
        return Map.of(
                "uid", user.getUid() != null ? user.getUid() : "",
                "realName", user.getRealName() != null ? user.getRealName() : "",
                "school", user.getSchool() != null ? user.getSchool() : "",
                "phone", user.getPhone() != null ? user.getPhone() : "",
                "email", user.getEmail() != null ? user.getEmail() : "",
                "studentNo", user.getStudentNo() != null ? user.getStudentNo() : ""
        );
    }

    private void cacheInfo(String cacheKey, Map<String, Object> info) {
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(info), USER_INFO_TTL);
        } catch (Exception e) {
            log.warn("缓存用户信息失败", e);
        }
    }

    /**
     * 添加签到日志
     */
    public void addSignLog(Long userId, String logType, String message) {
        SignLog log = SignLog.builder()
                .userId(userId)
                .logType(logType)
                .message(message)
                .build();
        signLogRepository.save(log);
    }

    @SuppressWarnings("unchecked")
    private void updateUserInfoFromResponse(User user, String body) {
        // 超星返回 JSON: {"msg":{"uid":"xxx","name":"xxx","schoolname":"xxx","phone":"xxx","email":"xxx","studentcode":"xxx"}}
        try {
            Map<String, Object> json = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            Object msgObj = json.get("msg");
            if (!(msgObj instanceof Map)) {
                log.warn("用户信息 JSON 中无 msg 对象: {}", body.substring(0, Math.min(200, body.length())));
                return;
            }
            Map<String, Object> msg = (Map<String, Object>) msgObj;

            user.setUid(strOrNull(msg, "uid"));
            user.setRealName(strOrNull(msg, "name"));
            user.setSchool(strOrNull(msg, "schoolname"));
            user.setPhone(strOrNull(msg, "phone"));
            // email: 超星 API 不返回 email 字段，设默认值
            String email = strOrNull(msg, "email");
            user.setEmail(email != null && !email.isEmpty() ? email : "未绑定");
            // studentcode: 优先用 studentcode，为空时用 uname（学号）兜底
            String studentcode = strOrNull(msg, "studentcode");
            if (studentcode != null && !studentcode.isEmpty()) {
                user.setStudentNo(studentcode);
            } else {
                user.setStudentNo(strOrNull(msg, "uname"));
            }

        } catch (Exception e) {
            log.warn("[UserService] 解析用户 JSON 失败: {}", e.getMessage());
        }
    }

    private String strOrNull(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
