package pe.dcs.app.features.pastoral_followup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.followup.FollowUpContactMethod;
import pe.dcs.app.util.enums.followup.FollowUpContactResult;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class FollowUpContactFormRequest {

    @NotNull(message = "{error.fechaContactoObligatoria}")
    private LocalDate contactDate;

    @NotNull(message = "{error.medioContactoObligatorio}")
    private FollowUpContactMethod contactMethod;

    @NotNull(message = "{error.resultadoContactoObligatorio}")
    private FollowUpContactResult result;

    private String notes;

    /**
     * Solo relevante para org admin (elige sede libremente); igual
     * criterio que FinancialMovementRequest.branchId. Branch admin/
     * org user delegado siempre usan su sede actual (ver
     * PastoralFollowUpAccessGuard.resolveBranchId).
     */
    private UUID branchId;
}
