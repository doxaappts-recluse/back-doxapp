package pe.dcs.app.features.visitor.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cada hora avisa de los visitantes NEW sin contacto pasado el plazo de su organización (una sola vez por caso). Se apaga con visitor.job-enabled=false. */
@Slf4j
@Component
@RequiredArgsConstructor
public class VisitorSlaJob {

    private final VisitorService service;

    @Value("${visitor.job-enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${visitor.sla-cron:0 10 * * * *}")
    public void run() {
        if (!enabled) {
            return;
        }
        int n = service.alertOverdue(null);
        if (n > 0) {
            log.info("[VISITOR-SLA] {} caso(s) sin contacto fuera de plazo", n);
        }
    }
}
