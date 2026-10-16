package pe.dcs.app.features.ministry.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Cada hora (minuto 15): vencimiento de verificaciones y avisos de riesgo. Se apaga con ministry.job-enabled=false. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinistryJob {

    private final MinistryMaintenanceService service;

    @Value("${ministry.job-enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${ministry.job-cron:0 15 * * * *}")
    public void run() {
        if (!enabled) {
            return;
        }
        Map<String, Integer> r = service.run();
        if (r.values().stream().anyMatch(v -> v > 0)) {
            log.info("[MINISTRY] {}", r);
        }
    }
}
