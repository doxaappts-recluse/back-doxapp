package pe.dcs.app.features.support.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.support.dto.MaintenanceResult;

/** Cada hora: alerta de SLA vencido (una sola vez por caso) y cierre automático por inactividad (apagable con support.job-enabled=false). */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupportMaintenanceJob {

    private final SupportService service;

    @Value("${support.job-enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "0 35 * * * *")
    public void run() {
        if (!enabled) {
            return;
        }
        try {
            MaintenanceResult r = service.maintenance();
            if (r.slaAlerted() > 0 || r.autoClosed() > 0) {
                log.info("Soporte: {} alertas de SLA, {} casos cerrados automáticamente", r.slaAlerted(), r.autoClosed());
            }
        } catch (Exception e) {
            log.error("Falló el mantenimiento de soporte", e);
        }
    }
}
