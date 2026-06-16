package chaoxing.autosign.controller;

import chaoxing.autosign.dto.ApiResponse;
import chaoxing.autosign.entity.SignRecord;
import chaoxing.autosign.entity.SignLog;
import chaoxing.autosign.repository.SignRecordRepository;
import chaoxing.autosign.repository.SignLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/sign-record")
@RequiredArgsConstructor
public class SignRecordController {

    private final SignRecordRepository signRecordRepository;
    private final SignLogRepository signLogRepository;

    @GetMapping("/list")
    public ApiResponse<Page<SignRecord>> getRecords(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = (Long) request.getAttribute("userId");
        Page<SignRecord> records = signRecordRepository
                .findByUserIdOrderBySignTimeDesc(userId, PageRequest.of(page - 1, size));
        return ApiResponse.success(records);
    }

    @GetMapping("/logs")
    public ApiResponse<Page<SignLog>> getLogs(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = (Long) request.getAttribute("userId");
        Page<SignLog> logs = signLogRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page - 1, size));
        return ApiResponse.success(logs);
    }

    @Transactional
    @DeleteMapping("/logs")
    public ApiResponse<Void> clearLogs(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        signLogRepository.deleteByUserId(userId);
        return ApiResponse.success("日志已清空", null);
    }

    @Transactional
    @DeleteMapping("/logs/{id}")
    public ApiResponse<Void> deleteLog(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        SignLog log = signLogRepository.findByIdAndUserId(id, userId)
                .orElse(null);
        if (log == null) {
            return ApiResponse.error("日志不存在或无权操作");
        }
        signLogRepository.delete(log);
        return ApiResponse.success("日志已删除", null);
    }
}
