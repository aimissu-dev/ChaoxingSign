package chaoxing.autosign.service;

import chaoxing.autosign.dto.SignConfigRequest;
import chaoxing.autosign.entity.SignConfig;
import chaoxing.autosign.entity.SignTimeWindow;
import chaoxing.autosign.repository.SignConfigRepository;
import chaoxing.autosign.repository.SignTimeWindowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SignConfigService {

    private final SignConfigRepository signConfigRepository;
    private final SignTimeWindowRepository timeWindowRepository;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String CONFIG_CACHE_PREFIX = "task:config:";
    private static final Duration CONFIG_CACHE_TTL = Duration.ofMinutes(30);

    /**
     * 获取用户的所有签到配置（附带时间窗口）
     */
    public List<SignConfig> getConfigs(Long userId) {
        List<SignConfig> configs = signConfigRepository.findByUserId(userId);
        if (configs.isEmpty()) return configs;

        // 批量加载时间窗口
        List<Long> configIds = configs.stream().map(SignConfig::getId).toList();
        List<SignTimeWindow> allWindows = timeWindowRepository.findByConfigIdIn(configIds);
        Map<Long, List<SignTimeWindow>> windowMap = allWindows.stream()
                .collect(Collectors.groupingBy(SignTimeWindow::getConfigId));

        for (SignConfig config : configs) {
            List<SignTimeWindow> windows = windowMap.getOrDefault(config.getId(), new ArrayList<>());
            // 兼容：DB 中无时间窗口但 entity 有旧字段时，自动创建一条
            if (windows.isEmpty() && hasLegacyWindow(config)) {
                windows.add(buildLegacyWindow(config));
            }
            config.setTimeWindows(windows);
        }
        return configs;
    }

    /**
     * 获取指定课程的签到配置
     */
    public Optional<SignConfig> getConfig(Long userId, String courseName) {
        return signConfigRepository.findByUserIdAndCourseName(userId, courseName);
    }

    /**
     * 新增签到配置（含时间窗口）
     */
    @Transactional
    public SignConfig addConfig(Long userId, SignConfigRequest request) {
        // 检查是否已存在
        if (signConfigRepository.findByUserIdAndCourseName(userId, request.getCourseName()).isPresent()) {
            throw new RuntimeException("该课程的签到配置已存在");
        }

        SignConfig config = buildConfig(userId, request);
        SignConfig saved = signConfigRepository.save(config);

        // 保存时间窗口
        saveTimeWindows(saved.getId(), request);

        clearConfigCache(userId);
        return enrichWithWindows(saved, request);
    }

    /**
     * 更新签到配置（含时间窗口）
     */
    @Transactional
    public SignConfig updateConfig(Long userId, Long configId, SignConfigRequest request) {
        SignConfig config = signConfigRepository.findById(configId)
                .orElseThrow(() -> new RuntimeException("签到配置不存在"));

        if (!config.getUserId().equals(userId)) {
            throw new RuntimeException("无权修改该配置");
        }

        config.setCourseName(request.getCourseName());
        config.setSignCode(request.getSignCode());
        config.setAddress(request.getAddress());
        config.setLatitude(request.getLatitude());
        config.setLongitude(request.getLongitude());
        config.setSignStartTime(request.getSignStartTime());
        config.setSignEndTime(request.getSignEndTime());

        SignConfig saved = signConfigRepository.save(config);

        // 先删后插时间窗口
        saveTimeWindows(saved.getId(), request);

        clearConfigCache(userId);
        return enrichWithWindows(saved, request);
    }

    /**
     * 删除签到配置（级联删除时间窗口）
     */
    @Transactional
    public void deleteConfig(Long userId, Long configId) {
        SignConfig config = signConfigRepository.findById(configId)
                .orElseThrow(() -> new RuntimeException("签到配置不存在"));

        if (!config.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除该配置");
        }

        timeWindowRepository.deleteAllByConfigId(configId);
        signConfigRepository.delete(config);
        clearConfigCache(userId);
    }

    // ==================== 时间窗口查询 ====================

    /**
     * 获取某个配置的所有时间窗口
     */
    public List<SignTimeWindow> getTimeWindows(Long configId) {
        List<SignTimeWindow> windows = timeWindowRepository.findByConfigId(configId);
        return windows.isEmpty() ? windows : windows;
    }

    // ==================== 私有方法 ====================

    private SignConfig buildConfig(Long userId, SignConfigRequest req) {
        return SignConfig.builder()
                .userId(userId)
                .courseName(req.getCourseName())
                .signCode(req.getSignCode())
                .address(req.getAddress())
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .signStartTime(req.getSignStartTime())
                .signEndTime(req.getSignEndTime())
                .build();
    }

    private void saveTimeWindows(Long configId, SignConfigRequest request) {
        // 先清除旧的时间窗口
        timeWindowRepository.deleteAllByConfigId(configId);

        // 优先使用新的多时间窗口列表
        if (!CollectionUtils.isEmpty(request.getTimeWindows())) {
            for (SignConfigRequest.TimeWindowItem item : request.getTimeWindows()) {
                timeWindowRepository.save(SignTimeWindow.builder()
                        .configId(configId)
                        .startTime(item.getStartTime())
                        .endTime(item.getEndTime())
                        .build());
            }
            return;
        }

        // 兼容：使用旧字段的单时间窗口
        if (hasLegacyFields(request)) {
            timeWindowRepository.save(SignTimeWindow.builder()
                    .configId(configId)
                    .startTime(request.getSignStartTime())
                    .endTime(request.getSignEndTime())
                    .build());
        }
    }

    private SignConfig enrichWithWindows(SignConfig config, SignConfigRequest request) {
        List<SignTimeWindow> windows = timeWindowRepository.findByConfigId(config.getId());
        // 如果 DB 为空但请求中有，手动构建返回
        if (windows.isEmpty()) {
            if (!CollectionUtils.isEmpty(request.getTimeWindows())) {
                windows = request.getTimeWindows().stream()
                        .map(item -> SignTimeWindow.builder()
                                .configId(config.getId())
                                .startTime(item.getStartTime())
                                .endTime(item.getEndTime())
                                .build())
                        .toList();
            } else if (hasLegacyFields(request)) {
                windows = List.of(buildLegacyWindow(config.getId(), request));
            }
        }
        config.setTimeWindows(windows);
        return config;
    }

    private boolean hasLegacyWindow(SignConfig config) {
        return config.getSignStartTime() != null && !config.getSignStartTime().isEmpty()
                && config.getSignEndTime() != null && !config.getSignEndTime().isEmpty();
    }

    private boolean hasLegacyFields(SignConfigRequest req) {
        return req.getSignStartTime() != null && !req.getSignStartTime().isEmpty()
                && req.getSignEndTime() != null && !req.getSignEndTime().isEmpty();
    }

    private SignTimeWindow buildLegacyWindow(SignConfig config) {
        return SignTimeWindow.builder()
                .configId(config.getId())
                .startTime(config.getSignStartTime())
                .endTime(config.getSignEndTime())
                .build();
    }

    private SignTimeWindow buildLegacyWindow(Long configId, SignConfigRequest req) {
        return SignTimeWindow.builder()
                .configId(configId)
                .startTime(req.getSignStartTime())
                .endTime(req.getSignEndTime())
                .build();
    }

    private void clearConfigCache(Long userId) {
        redisTemplate.delete(CONFIG_CACHE_PREFIX + userId);
    }
}
