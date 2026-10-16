package pe.dcs.app.features.transfer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.approval.service.ApprovalHandler;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** M21 · Handler del tipo BRANCH_TRANSFER: decide la sede destino (acción A de BRANCH_TRANSFER); la sede origen es notificada al aprobarse. */
@Component
@RequiredArgsConstructor
public class BranchTransferHandler implements ApprovalHandler {

    private final BranchTransferService transfers;
    private final NotificationService notifications;

    @Override
    public String type() {
        return "BRANCH_TRANSFER";
    }

    @Override
    public String moduleCode() {
        return BranchTransferService.MODULE;
    }

    @Override
    public String notificationPrefix() {
        return "TRANSFER";
    }

    @Override
    public Map<String, String> notifyParams(ApprovalRow request) {
        return transfers.params(request.id());
    }

    @Override
    public void onApprove(Decision d) {
        transfers.mark(d.request().id(), "APPROVED");
        UUID from = transfers.fromBranchOf(d.request().id());
        if (from != null) {
            List<UUID> to = new ArrayList<>(notifications.branchAdmins(d.request().organizationId(), from));
            to.remove(d.request().requestedBy());
            notifications.toPersons(NotificationType.TRANSFER_ORIGIN, d.request().organizationId(), to, transfers.params(d.request().id()),
                    "/app/transfers", null);
        }
    }

    @Override
    public void onReject(Decision d) {
        transfers.mark(d.request().id(), "REJECTED");
    }

    @Override
    public void onCancel(Decision d) {
        transfers.mark(d.request().id(), "CANCELLED");
    }

    @Override
    public void onExpire(Decision d) {
        transfers.mark(d.request().id(), "EXPIRED");
    }
}
