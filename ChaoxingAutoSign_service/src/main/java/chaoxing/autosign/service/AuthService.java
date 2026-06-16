package chaoxing.autosign.service;

import chaoxing.autosign.dto.LoginRequest;
import chaoxing.autosign.dto.LoginResponse;
import chaoxing.autosign.entity.User;
import chaoxing.autosign.repository.UserRepository;
import chaoxing.autosign.util.AesUtil;
import chaoxing.autosign.util.JwtUtil;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String CHAOXING_LOGIN_URL = "https://passport2.chaoxing.com/fanyalogin";
    private static final String COOKIE_CACHE_PREFIX = "cookie:";
    private static final String TOKEN_CACHE_PREFIX = "token:";
    private static final Duration COOKIE_TTL = Duration.ofHours(2);
    private static final Duration TOKEN_TTL = Duration.ofDays(7);

    /**
     * 用户登录：先调用超星 API，成功后存储用户信息并生成 JWT
     */
    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();

        // 调用超星登录接口
        Map<String, Object> loginResult = callChaoxingLogin(username, password);

        boolean loginSuccess = (boolean) loginResult.get("status");
        String message = (String) loginResult.getOrDefault("message", "");

        if (!loginSuccess) {
            throw new RuntimeException(message.isEmpty() ? "登录失败，请检查用户名和密码" : message);
        }

        // 获取 Cookie
        @SuppressWarnings("unchecked")
        List<String> cookies = (List<String>) loginResult.get("cookies");
        String cookiesRaw = String.join("; ", cookies);

        // 查找或创建用户
        User user = userRepository.findByUsername(username).orElseGet(() -> {
            User newUser = User.builder()
                    .username(username)
                    .passwordEnc(AesUtil.encrypt(password))
                    .cookiesRaw(cookiesRaw)
                    .status(1)
                    .build();
            return userRepository.save(newUser);
        });

        // 更新 Cookie
        user.setCookiesRaw(cookiesRaw);
        user.setPasswordEnc(AesUtil.encrypt(password));
        userRepository.save(user);

        // 缓存 Cookie 到 Redis
        redisTemplate.opsForValue().set(COOKIE_CACHE_PREFIX + user.getId(), cookiesRaw, COOKIE_TTL);

        // 生成 JWT Token
        String token = jwtUtil.generateToken(user.getId(), username);
        redisTemplate.opsForValue().set(TOKEN_CACHE_PREFIX + user.getId(), token, TOKEN_TTL);

        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(username)
                .realName(user.getRealName())
                .uid(user.getUid())
                .cookieValid(true)
                .build();
    }

    /**
     * 调用超星学习通登录接口
     * 以 Cookie 中是否包含 _uid / p_auth_token 判断登录成功
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callChaoxingLogin(String username, String password) {
        try {
            String encryptedUser = AesUtil.encrypt(username);
            String encryptedPass = AesUtil.encrypt(password);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("fid", "-1");
            body.add("uname", encryptedUser);
            body.add("password", encryptedPass);
            body.add("refer", "");
            body.add("t", "true");
            body.add("forbidotherlogin", "0");
            body.add("validate", "");
            body.add("doubleFactorLogin", "0");
            body.add("independentId", "0");

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

            // 使用 String.class 接收任意 Content-Type 的响应
            ResponseEntity<String> response = restTemplate.exchange(
                    CHAOXING_LOGIN_URL,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            // 获取响应 Cookie
            List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
            List<String> cookies = setCookies != null ? setCookies : List.of();

            // 通过 Cookie 中是否有 _uid 或 p_auth_token 判断登录是否成功
            boolean hasAuthCookie = cookies.stream()
                    .anyMatch(c -> c.contains("_uid=") || c.contains("p_auth_token="));

            // 尝试解析响应体中的错误消息
            String respBody = response.getBody();
            String errorMsg = "";
            if (respBody != null && respBody.contains("\"status\":false")) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> result = mapper.readValue(respBody, Map.class);
                    errorMsg = (String) result.getOrDefault("msg2", "");
                } catch (Exception ignored) {
                    // 非 JSON 响应，忽略
                }
            }

            if (!hasAuthCookie) {
                log.warn("超星登录失败: username={}, cookies={}, body excerpt={}",
                        username, cookies, respBody != null ? respBody.substring(0, Math.min(200, respBody.length())) : "");
            }

            boolean success = hasAuthCookie;
            String message = success ? "登录成功" : (errorMsg.isEmpty() ? "用户名或密码错误" : errorMsg);

            return Map.of(
                    "status", success,
                    "message", message,
                    "cookies", cookies
            );
        } catch (Exception e) {
            log.error("调用超星登录接口失败", e);
            return Map.of("status", false, "message", "登录服务异常: " + e.getMessage(), "cookies", List.of());
        }
    }

    /**
     * 从 Redis 获取用户 Cookie
     */
    public String getCookieFromCache(Long userId) {
        String cached = (String) redisTemplate.opsForValue().get(COOKIE_CACHE_PREFIX + userId);
        if (cached != null) {
            return cached;
        }
        // 回源数据库
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent() && userOpt.get().getCookiesRaw() != null) {
            String cookies = userOpt.get().getCookiesRaw();
            redisTemplate.opsForValue().set(COOKIE_CACHE_PREFIX + userId, cookies, COOKIE_TTL);
            return cookies;
        }
        return null;
    }

    /**
     * 用数据库中存储的凭证重新登录超星，刷新 Cookie
     * @return 新的 Cookie 字符串，失败返回 null
     */
    public String refreshCookie(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("刷新Cookie失败: 用户不存在 userId={}", userId);
            return null;
        }

        User user = userOpt.get();
        String password;
        try {
            password = AesUtil.decrypt(user.getPasswordEnc());
        } catch (Exception e) {
            log.error("刷新Cookie失败: 密码解密异常 userId={}", userId, e);
            return null;
        }

        Map<String, Object> result = callChaoxingLogin(user.getUsername(), password);
        boolean success = (boolean) result.get("status");
        if (!success) {
            log.warn("刷新Cookie失败: 重新登录失败 userId={}, msg={}", userId, result.get("message"));
            return null;
        }

        @SuppressWarnings("unchecked")
        List<String> cookies = (List<String>) result.get("cookies");
        String cookiesRaw = String.join("; ", cookies);

        // 更新 DB
        user.setCookiesRaw(cookiesRaw);
        userRepository.save(user);

        // 更新 Redis 缓存
        redisTemplate.opsForValue().set(COOKIE_CACHE_PREFIX + userId, cookiesRaw, COOKIE_TTL);

        log.info("Cookie 已刷新: userId={}", userId);
        return cookiesRaw;
    }
}
