package pe.dcs.app.features.support.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * M22 (D3) · cada 10 min: marca EXPIRED los grants ACTIVE vencidos. Housekeeping puro — la revocación real ya la
 * aplica {@code AuthorizationService.assistedStaffActions} leyendo el grant en vivo en cada permiso, así que un
 * atraso de este job nunca deja una sesión asistida vigente más allá de su expiresAt real. Se apaga con
 * assisted.job-enabled=false.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssistedAccessJob {

    private final AssistedAccessService service;

    @Value("${assisted.job-enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${assisted.job-cron:0 */10 * * * *}")
    public void run() {
        if (!enabled) {
            return;
        }
        int n = service.expireOverdue();
        if (n > 0) {
            log.info("[ASSISTED] {} grant(s) marcados EXPIRED", n);
        }
    }
}
