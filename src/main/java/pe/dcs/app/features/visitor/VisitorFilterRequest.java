package pe.dcs.app.features.visitor;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.visitor.VisitorConsolidationStage;
import pe.dcs.app.util.enums.visitor.VisitorHowArrived;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class VisitorFilterRequest {

    /**
     * Busca por nombre/apellido/dni de la persona (contains,
     * case-insensitive para nombre/apellido) — ver
     * VisitorSpecification.
     */
    private String personName;

    private VisitorHowArrived howArrived;

    private VisitorConsolidationStage consolidationStage;

    private LocalDate startDate;

    private LocalDate endDate;

    /**
     * Solo relevante para org admin/SYSTEM; para branch admin/org
     * user delegado el scope ya lo fija VisitorSpecification con la
     * sede actual.
     */
    private UUID branchId;
}
