package pe.dcs.app.features.approval.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Cada hora (minuto 40). Se apaga con approval.job-enabled=false. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalMaintenanceJob {

    private final ApprovalMaintenanceService service;

    @Value("${approval.job-enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${approval.maintenance-cron:0 40 * * * *}")
    public void run() {
        if (!enabled) {
            return;
        }
        Map<String, Integer> r = service.run();
        if (r.values().stream().anyMatch(v -> v > 0)) {
            log.info("[APPROVAL-JOB] {}", r);
        }
    }
}
