package pe.dcs.app.features.branch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.approval.service.ApprovalHandler;
import pe.dcs.app.features.branch.dto.BranchCreateRequest;
import pe.dcs.app.features.organization.dto.AddressDto;

import java.time.LocalDate;
import java.util.Map;

/**
 * M22 · handler del tipo {@code NEW_BRANCH_REQUEST}. {@code SupportService.open()} lo crea automáticamente al abrir un
 * caso NEW_BRANCH, con el payload que envió el ORG_ADMIN (mismos campos que {@code NewBranchDraft}, guardados tal
 * cual en {@code approval_request.payload}). Cuando SYSTEM_ADMIN resuelve el caso, {@code SupportService.changeStatus}
 * aprueba esta solicitud ({@code ApprovalEngine.decideInternal}), lo que dispara {@link #onApprove}: a diferencia de
 * CONTRACT_REQUEST, aquí SÍ se ejecuta de verdad — crear una sede no exige una decisión de precio/plan, solo los datos
 * que ya mandó la organización, así que se reutiliza {@link BranchService#create} sin reescribir su validación
 * ([V6] tope {@code maxBranches} incluido). Si el contrato ya no tiene cupo, {@code create} lanza
 * {@code error.branch.maxReached}: como {@code onApprove} corre dentro de la transacción de la decisión, esta no se
 * aplica (el caso sigue abierto) y el staff responde a mano por el hilo del caso sugiriendo un upgrade de contrato —
 * ese paso "guiado" del spec (M22-T12) es intencionalmente manual, no algo que este handler intente automatizar.
 */
@Component
@RequiredArgsConstructor
public class NewBranchRequestHandler implements ApprovalHandler {

    private final BranchService branches;

    @Override
    public String type() {
        return "NEW_BRANCH_REQUEST";
    }

    @Override
    public String moduleCode() {
        return BranchService.MODULE_N1;
    }

    @Override
    public void onApprove(Decision d) {
        Map<String, Object> p = d.request().payload();
        BranchCreateRequest req = new BranchCreateRequest(
                str(p, "name"), str(p, "code"), str(p, "displayName"),
                new AddressDto(str(p, "addressLine"), null, str(p, "addressCity"), null, str(p, "addressCountry"), null),
                str(p, "phone"), str(p, "email"),
                p.get("openingDate") == null ? null : LocalDate.parse(str(p, "openingDate")),
                str(p, "timezone"), null);
        branches.create(d.request().organizationId(), req);
    }

    private static String str(Map<String, Object> p, String key) {
        Object v = p.get(key);
        return v == null ? null : v.toString();
    }
}
