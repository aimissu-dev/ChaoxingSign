package chaoxing.autosign.controller;

import chaoxing.autosign.dto.ApiResponse;
import chaoxing.autosign.entity.Course;
import chaoxing.autosign.service.AuthService;
import chaoxing.autosign.service.CourseService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/course")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final AuthService authService;
    private final RestTemplate restTemplate;

    /**
     * 获取课程列表（从超星拉取 + 持久化到 DB）
     */
    @GetMapping("/list")
    public ApiResponse<List<Course>> getCourseList(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        // 从超星 API 获取课程（HTML解析 + 进度）
        List<Map<String, Object>> rawList = courseService.getCourseList(userId);

        // 持久化到数据库（upsert）
        List<Course> courses = courseService.saveCoursesFromRaw(userId, rawList);

        return ApiResponse.success(courses);
    }

    /**
     * 获取文件夹列表（从 interaction 页面解析）
     */
    @GetMapping("/folders")
    public ApiResponse<List<Map<String, String>>> getFolders(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<Map<String, String>> folders = courseService.getFolderList(userId);
        return ApiResponse.success(folders);
    }

    /**
     * 获取课程表（从 kb.chaoxing.com 同步）
     */
    @GetMapping("/schedule")
    public ApiResponse<Map<String, Object>> getSchedule(
            HttpServletRequest request,
            @RequestParam(required = false) Integer week) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> data = courseService.getSchedule(userId, week);
        return ApiResponse.success(data);
    }

    /**
     * 封面图代理（解决跨域问题）
     * 前端用 Base64 编码目标 URL，后端代理请求并返回图片
     * 注意：此接口已排除 JWT 认证（img 标签无法带 Authorization 头）
     */
    @GetMapping("/cover")
    public ResponseEntity<byte[]> proxyCover(
            HttpServletRequest request,
            @RequestParam("url") String encodedUrl) {
        String targetUrl;
        try {
            targetUrl = new String(Base64.getUrlDecoder().decode(encodedUrl));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }

        // 仅允许代理超星域名的图片
        if (!targetUrl.contains("chaoxing.com")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            // 尝试带 Cookie（可选，封面图通常公开可访问）
            Long userId = (Long) request.getAttribute("userId");
            String cookies = userId != null ? authService.getCookieFromCache(userId) : null;

            HttpHeaders headers = new HttpHeaders();
            if (cookies != null) headers.set("Cookie", cookies);
            headers.set("User-Agent", "Mozilla/5.0");
            headers.set("Referer", "https://mooc2-ans.chaoxing.com/");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<byte[]> imageResp = restTemplate.exchange(
                    targetUrl, HttpMethod.GET, entity, byte[].class);

            HttpHeaders respHeaders = new HttpHeaders();
            MediaType contentType = imageResp.getHeaders().getContentType();
            respHeaders.setContentType(contentType != null ? contentType : MediaType.IMAGE_JPEG);
            respHeaders.setCacheControl("public, max-age=3600");

            return new ResponseEntity<>(imageResp.getBody(), respHeaders, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
