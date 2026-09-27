package pe.dcs.app.features.report.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** M20 · Dispara {@link ScheduledReportService#runDue()} una vez al día (mismo patrón que {@code ContractLifecycleJob}). */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledReportJob {

    private final ScheduledReportService scheduledReports;

    @Scheduled(cron = "0 30 6 * * *")
    public void daily() {
        try {
            scheduledReports.runDue();
        } catch (RuntimeException e) {
            log.error("Falló la tarea de reportes programados", e);
        }
    }
}
