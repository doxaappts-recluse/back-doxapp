package pe.dcs.app.features.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Limpieza diaria de avisos leídos antiguos (03:40). Se apaga con notification.job-enabled=false. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupJob {

    private final NotificationService service;

    @Value("${notification.job-enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${notification.cleanup-cron:0 40 3 * * *}")
    public void run() {
        if (!enabled) {
            return;
        }
        int n = service.purgeOld();
        if (n > 0) {
            log.info("Avisos antiguos eliminados: {}", n);
        }
    }
}
