package pe.dcs.app.features.marriage.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.finance.FinancialMovementPaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class MarriageFormRequest {

    /**
     * Setear solo si el cónyuge se encontró por DNI (ver
     * MarriageController.findSpouseByDni). Si es null, el registro
     * queda con spouseName en texto libre nada más — no se crea una
     * Person nueva.
     */
    private UUID spouse1PersonId;
    private String spouse1Name;
    private String spouse1Dni;

    private UUID spouse2PersonId;
    private String spouse2Name;
    private String spouse2Dni;

    private LocalDate marriageDate;

    private String churchName;

    private String pastorName;

    private String city;

    private boolean verified;

    private String observations;

    /**
     * Opcional: si viene informado (> 0) se crea automáticamente el
     * FinancialMovement de categoría SERVICE_FEE enlazado a este
     * registro (ver MarriageServiceImpl).
     */
    private BigDecimal feeAmount;

    private FinancialMovementPaymentMethod feePaymentMethod;

    /**
     * Solo relevante para org admin (elige sede libremente); igual
     * criterio que FinancialMovementRequest.branchId /
     * FinancialMovementServiceImpl.resolveBranch.
     */
    private UUID branchId;
}
