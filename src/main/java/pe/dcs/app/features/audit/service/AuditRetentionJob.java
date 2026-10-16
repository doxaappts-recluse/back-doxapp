package pe.dcs.app.features.audit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Purga diaria de auditoría según la política de retención (apagable con audit.retention.job-enabled=false). */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditRetentionJob {

    private final RetentionService retention;

    @Value("${audit.retention.job-enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "0 30 3 * * *")
    public void run() {
        if (!enabled) {
            return;
        }
        try {
            retention.purge();
        } catch (Exception e) {
            log.error("Falló la purga de auditoría", e);
        }
    }
}
