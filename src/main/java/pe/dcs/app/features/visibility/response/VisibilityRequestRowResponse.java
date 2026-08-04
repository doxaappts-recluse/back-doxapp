package pe.dcs.app.features.visibility.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;
import pe.dcs.app.util.enums.rules.VisibilityStatus;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class VisibilityRequestRowResponse extends AuditableResponse {

    private UUID id;

    private UUID personId;
    private String personName;
    private String personLastname;

    private String moduleCode;
    private String moduleName;

    private UUID sourceBranchId;
    private String sourceBranchName;

    private UUID requestBranchId;
    private String requestBranchName;

    private String requestedByName;

    private String reason;

    private LocalDate requestedFrom;
    private LocalDate requestedUntil;
    private LocalDate approvedUntil;

    private VisibilityStatus status;

    /**
     * Solo relevante en el listado "entrantes": ¿el que consulta
     * puede aprobar/rechazar esta solicitud puntual?
     */
    private boolean canDecide;
}
