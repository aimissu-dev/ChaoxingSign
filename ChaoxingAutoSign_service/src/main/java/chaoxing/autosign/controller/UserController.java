package chaoxing.autosign.controller;

import chaoxing.autosign.dto.ApiResponse;
import chaoxing.autosign.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> getUserInfo(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> info = userService.getUserInfo(userId);
        return ApiResponse.success(info);
    }
}
