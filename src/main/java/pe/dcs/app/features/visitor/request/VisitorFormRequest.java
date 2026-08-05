package pe.dcs.app.features.visitor.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.visitor.VisitorConsolidationStage;
import pe.dcs.app.util.enums.visitor.VisitorHowArrived;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class VisitorFormRequest {

    @NotNull(message = "{error.fechaPrimeraVisitaObligatoria}")
    private LocalDate firstVisitDate;

    @NotNull(message = "{error.comoLlegoVisitanteObligatorio}")
    private VisitorHowArrived howArrived;

    /**
     * Solo relevante si howArrived=INVITED_BY_MEMBER (ver
     * VisitorServiceImpl.findInviterByDni para buscarlo por DNI,
     * igual patrón que MarriageServiceImpl.findSpouseByDni).
     */
    private UUID invitedByPersonId;

    @NotNull(message = "{error.etapaConsolidacionObligatoria}")
    private VisitorConsolidationStage consolidationStage;

    private String notes;

    /**
     * Solo relevante para org admin (elige sede libremente); igual
     * criterio que MarriageFormRequest.branchId.
     */
    private UUID branchId;
}
