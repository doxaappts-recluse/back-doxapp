package pe.dcs.app.features.visitor.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.visitor.VisitorConsolidationStage;
import pe.dcs.app.util.enums.visitor.VisitorHowArrived;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class VisitorFormRequest {

    private LocalDate firstVisitDate;

    private VisitorHowArrived howArrived;

    /**
     * Solo relevante si howArrived=INVITED_BY_MEMBER (ver
     * VisitorServiceImpl.findInviterByDni para buscarlo por DNI,
     * igual patrón que MarriageServiceImpl.findSpouseByDni).
     */
    private UUID invitedByPersonId;

    private VisitorConsolidationStage consolidationStage;

    private String notes;

    /**
     * Solo relevante para org admin (elige sede libremente); igual
     * criterio que MarriageFormRequest.branchId.
     */
    private UUID branchId;
}
