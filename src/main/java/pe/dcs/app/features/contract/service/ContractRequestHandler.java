package pe.dcs.app.features.contract.service;

import org.springframework.stereotype.Component;
import pe.dcs.app.features.approval.service.ApprovalHandler;

/**
 * M22 · handler del tipo {@code CONTRACT_REQUEST}. {@code SupportService.open()} lo crea automáticamente al abrir un
 * caso de soporte con categoría CONTRACT_CHANGE (upgrade/downgrade/renovación). El cambio de términos real (plan,
 * precio, fechas) lo decide y ejecuta SYSTEM_ADMIN a mano en M03 ({@link ContractService#transition}) — no algo que
 * este handler pueda automatizar, porque exige juicio de negocio, no solo un campo de "aprobar". Cuando SYSTEM_ADMIN
 * resuelve el caso desde el panel de soporte, {@code SupportService.changeStatus} aprueba esta solicitud
 * ({@code ApprovalEngine.decideInternal}), lo que dispara {@link #onApprove}: aquí no queda nada más por hacer, solo
 * cerrar el ciclo (el registro de auditoría de la aprobación ya lo escribe {@code ApprovalEngine} por su cuenta).
 */
@Component
public class ContractRequestHandler implements ApprovalHandler {

    @Override
    public String type() {
        return "CONTRACT_REQUEST";
    }

    @Override
    public String moduleCode() {
        return ContractService.MODULE;
    }

    @Override
    public void onApprove(Decision d) {
        // sin acción: el versionado del contrato ya lo hizo SYSTEM_ADMIN a mano en M03 antes de resolver el caso.
    }
}
