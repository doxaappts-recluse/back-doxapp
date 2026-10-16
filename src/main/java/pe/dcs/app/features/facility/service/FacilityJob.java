package pe.dcs.app.features.facility.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Cada hora: asignaciones vencidas. Se apaga con facility.job-enabled=false. */
@Slf4j
@Component
@RequiredArgsConstructor
public class FacilityJob {

    private final FacilityMaintenanceService service;

    @Value("${facility.job-enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${facility.job-cron:0 10 * * * *}")
    public void run() {
        if (!enabled) {
            return;
        }
        Map<String, Integer> r = service.run();
        if (r.values().stream().anyMatch(v -> v > 0)) {
            log.info("[FACILITY] {}", r);
        }
    }
}
