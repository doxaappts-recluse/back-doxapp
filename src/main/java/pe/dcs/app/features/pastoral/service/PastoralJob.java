package pe.dcs.app.features.pastoral.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cada hora revisa los plazos de los casos pastorales (aviso y escalamiento). Se apaga con pastoral.job-enabled=false. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PastoralJob {

    private final PastoralMaintenanceService service;

    @Value("${pastoral.job-enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${pastoral.sla-cron:0 20 * * * *}")
    public void run() {
        if (!enabled) {
            return;
        }
        var r = service.run();
        if (r.values().stream().mapToInt(Integer::intValue).sum() > 0) {
            log.info("[PASTORAL-SLA] {}", r);
        }
    }
}
