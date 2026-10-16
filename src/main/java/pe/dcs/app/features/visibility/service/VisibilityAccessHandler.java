package pe.dcs.app.features.visibility.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.approval.service.ApprovalHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * M21 · Handler del tipo VISIBILITY_ACCESS: la sede dueña de los datos decide (acción A de VISIBILITY_RULES). El nombre de la persona no se
 * muestra a quien no ve la sede que decide, y la solicitud vence a los 30 días si nadie la atiende.
 */
@Component
@RequiredArgsConstructor
public class VisibilityAccessHandler implements ApprovalHandler {

    private final VisibilityGrantService grants;

    @Override
    public String type() {
        return "VISIBILITY_ACCESS";
    }

    @Override
    public String moduleCode() {
        return VisibilityGrantService.MODULE;
    }

    @Override
    public String notificationPrefix() {
        return "VISIBILITY";
    }

    @Override
    public Integer expiryDays() {
        return 30;
    }

    @Override
    public boolean revealSubject() {
        return false;
    }

    @Override
    public Map<String, String> notifyParams(ApprovalRow r) {
        Map<String, String> m = new HashMap<>();
        m.put("module", String.valueOf(r.payload().get("moduleCode")));
        m.put("target", String.valueOf(r.payload().get("targetBranchName")));
        m.put("source", String.valueOf(r.payload().get("sourceBranchName")));
        m.put("until", String.valueOf(r.payload().get("visibleUntil")));
        return m;
    }

    @Override
    public void onApprove(Decision d) {
        grants.grantFromRequest(d.request(), d.actorPersonId());
    }
}
