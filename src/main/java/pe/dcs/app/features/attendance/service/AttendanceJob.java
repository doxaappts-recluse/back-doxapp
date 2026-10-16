package pe.dcs.app.features.attendance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Cada hora (minuto 5): sesiones del día, cierre de sesiones olvidadas y alertas de inasistencia. Se apaga con attendance.job-enabled=false. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceJob {

    private final AttendanceMaintenanceService service;

    @Value("${attendance.job-enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${attendance.job-cron:0 5 * * * *}")
    public void run() {
        if (!enabled) {
            return;
        }
        Map<String, Integer> r = service.run();
        if (r.values().stream().anyMatch(v -> v > 0)) {
            log.info("[ATTENDANCE] {}", r);
        }
    }
}
