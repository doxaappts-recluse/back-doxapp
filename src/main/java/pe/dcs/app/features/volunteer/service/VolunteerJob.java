package pe.dcs.app.features.volunteer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Cada 15 minutos: recordatorios de turno. Se apaga con volunteer.job-enabled=false. */
@Slf4j
@Component
@RequiredArgsConstructor
public class VolunteerJob {

    private final VolunteerMaintenanceService service;

    @Value("${volunteer.job-enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${volunteer.job-cron:0 */15 * * * *}")
    public void run() {
        if (!enabled) {
            return;
        }
        Map<String, Integer> r = service.run();
        if (r.values().stream().anyMatch(v -> v > 0)) {
            log.info("[VOLUNTEER_SCHEDULING] {}", r);
        }
    }
}
