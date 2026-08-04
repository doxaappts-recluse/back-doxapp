package pe.dcs.app.features.finance.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.finance.FinancialMovementCategory;
import pe.dcs.app.util.enums.finance.FinancialMovementStatus;
import pe.dcs.app.util.enums.finance.FinancialMovementType;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class FinancialMovementFilter {

    /**
     * Solo relevante para org admin/SYSTEM (acotar a una sede
     * puntual dentro de su alcance); para branch admin/org user
     * delegado el scope ya lo fija
     * FinancialMovementSpecification con la sede actual.
     */
    private UUID branchId;

    private FinancialMovementType type;

    private FinancialMovementCategory category;

    private FinancialMovementStatus status;

    private UUID personId;

    private UUID fundId;

    private LocalDate startDate;

    private LocalDate endDate;

    /**
     * true = filtra solo movimientos SIN donante identificado
     * (person IS NULL) — el bucket "Donante anónimo" del listado de
     * Donantes (ver FinancialMovementServiceImpl.donors()). Mutuamente
     * excluyente con personId en la práctica (el front nunca manda
     * ambos a la vez), pero no se valida acá: si llegaran los dos,
     * simplemente no habría resultados (AND de predicados
     * contradictorios).
     */
    private Boolean onlyAnonymous;
}
