package pe.dcs.app.features.contract.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Dispara {@link ContractLifecycleService#run()} cada hora (cubre todas las zonas horarias) y una vez al arrancar. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractLifecycleJob {

    private final ContractLifecycleService lifecycle;

    @Scheduled(cron = "0 20 * * * *")
    public void hourly() {
        safeRun();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        safeRun();
    }

    private void safeRun() {
        try {
            lifecycle.run();
        } catch (RuntimeException e) {
            log.error("Falló la tarea de contratos", e);
        }
    }
}
