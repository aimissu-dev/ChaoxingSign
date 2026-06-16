package chaoxing.autosign.controller;

import chaoxing.autosign.dto.ApiResponse;
import chaoxing.autosign.dto.SignConfigRequest;
import chaoxing.autosign.entity.SignConfig;
import chaoxing.autosign.service.SignConfigService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sign-config")
@RequiredArgsConstructor
public class SignConfigController {

    private final SignConfigService signConfigService;

    @GetMapping("/list")
    public ApiResponse<List<SignConfig>> getConfigList(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<SignConfig> configs = signConfigService.getConfigs(userId);
        return ApiResponse.success(configs);
    }

    @PostMapping
    public ApiResponse<SignConfig> addConfig(HttpServletRequest request,
                                              @Valid @RequestBody SignConfigRequest req) {
        Long userId = (Long) request.getAttribute("userId");
        SignConfig config = signConfigService.addConfig(userId, req);
        return ApiResponse.success("添加成功", config);
    }

    @PutMapping("/{id}")
    public ApiResponse<SignConfig> updateConfig(HttpServletRequest request,
                                                 @PathVariable Long id,
                                                 @Valid @RequestBody SignConfigRequest req) {
        Long userId = (Long) request.getAttribute("userId");
        SignConfig config = signConfigService.updateConfig(userId, id, req);
        return ApiResponse.success("更新成功", config);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteConfig(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        signConfigService.deleteConfig(userId, id);
        return ApiResponse.success("删除成功", null);
    }
}
