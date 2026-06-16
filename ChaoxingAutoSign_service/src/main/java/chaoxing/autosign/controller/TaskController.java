package chaoxing.autosign.controller;

import chaoxing.autosign.dto.ApiResponse;
import chaoxing.autosign.service.SignTaskService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/task")
@RequiredArgsConstructor
public class TaskController {

    private final SignTaskService signTaskService;

    @PostMapping("/start")
    public ApiResponse<Void> startTask(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        signTaskService.startTask(userId);
        return ApiResponse.success("任务已启动", null);
    }

    @PostMapping("/stop")
    public ApiResponse<Void> stopTask(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        signTaskService.stopTask(userId);
        return ApiResponse.success("任务已停止", null);
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getTaskStatus(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> status = signTaskService.getTaskStatus(userId);
        return ApiResponse.success(status);
    }
}
