package chaoxing.autosign.scheduler;

import chaoxing.autosign.service.SignTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 签到任务调度器
 * 每 30 秒执行一次签到循环
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignScheduler {

    private final SignTaskService signTaskService;

    @Scheduled(fixedDelay = 30000)
    public void executeSign() {
        log.debug("定时签到检查开始...");
        try {
            signTaskService.executeSignLoop();
        } catch (Exception e) {
            log.error("定时签到检查异常", e);
        }
    }
}
