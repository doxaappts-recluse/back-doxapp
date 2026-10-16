package pe.dcs.app.features.access.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Dispara {@link AccessExpiryService#run()} cada hora (minuto 25) y una vez al arrancar. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessExpiryJob {

    private final AccessExpiryService expiry;

    @Scheduled(cron = "0 25 * * * *")
    public void hourly() {
        safe();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        safe();
    }

    private void safe() {
        try {
            int n = expiry.run();
            if (n > 0) {
                log.info("Accesos vencidos pasados a INACTIVE: {}", n);
            }
        } catch (RuntimeException e) {
            log.error("Falló la tarea de vigencia de accesos", e);
        }
    }
}
