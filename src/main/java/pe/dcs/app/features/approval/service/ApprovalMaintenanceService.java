package pe.dcs.app.features.approval.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pe.dcs.app.features.transfer.service.BranchTransferService;
import pe.dcs.app.features.visibility.service.VisibilityGrantService;

import java.util.LinkedHashMap;
import java.util.Map;

/** M21 · Tareas periódicas: vencimiento de autorizaciones [V12] y de solicitudes [V6], recordatorio de solicitudes olvidadas y ejecución de traslados aprobados cuya fecha llegó. */
@Service
@RequiredArgsConstructor
public class ApprovalMaintenanceService {

    private final ApprovalEngine engine;
    private final VisibilityGrantService grants;
    private final BranchTransferService transfers;

    @Value("${approval.reminder-days:3}")
    private int reminderDays;

    public Map<String, Integer> run() {
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("expiredGrants", grants.expireDue());
        out.put("expiredRequests", engine.expirePending());
        out.put("executedTransfers", transfers.executeDue());
        out.put("reminders", engine.remindStale(reminderDays));
        return out;
    }
}
