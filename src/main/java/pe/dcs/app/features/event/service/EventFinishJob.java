package pe.dcs.app.features.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cada hora pasa a FINISHED los eventos PUBLISHED cuya fecha de fin ya pasó (además del botón manual). Se apaga con event.job-enabled=false. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventFinishJob {

    private final EventService service;

    @Value("${event.job-enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${event.finish-cron:0 15 * * * *}")
    public void run() {
        if (!enabled) {
            return;
        }
        int n = service.autoFinishDue();
        if (n > 0) {
            log.info("[EVENT-FINISH] {} evento(s) finalizado(s) automáticamente", n);
        }
    }
}
