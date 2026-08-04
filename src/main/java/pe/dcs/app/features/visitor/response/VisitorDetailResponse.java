package pe.dcs.app.features.visitor.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.visitor.VisitorConsolidationStage;
import pe.dcs.app.util.enums.visitor.VisitorHowArrived;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class VisitorDetailResponse {

    private UUID id;

    private UUID personId;
    private String personName;
    private String personLastname;
    private String personDni;

    private LocalDate firstVisitDate;

    private VisitorHowArrived howArrived;

    private UUID invitedByPersonId;
    private String invitedByName;

    private VisitorConsolidationStage consolidationStage;

    private LocalDate convertedAt;

    private String notes;

    private UUID branchId;
    private String branchName;

    /**
     * true si esta Visitor.person ya tiene una Membership vigente —
     * el front usa esto para ocultar el botón "Convertir a miembro"
     * si ya se convirtió (ver VisitorServiceImpl.convertToMember).
     */
    private boolean alreadyMember;
}
