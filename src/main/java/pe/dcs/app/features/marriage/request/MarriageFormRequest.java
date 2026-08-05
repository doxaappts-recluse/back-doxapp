package pe.dcs.app.features.marriage.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotBlank(message = "{error.nombrePrimerConyugeObligatorio}")
    private String spouse1Name;
    private String spouse1Dni;

    private UUID spouse2PersonId;

    @NotBlank(message = "{error.nombreSegundoConyugeObligatorio}")
    private String spouse2Name;
    private String spouse2Dni;

    @NotNull(message = "{error.fechaMatrimonioObligatoria}")
    private LocalDate marriageDate;

    @NotBlank(message = "{error.iglesiaDondeCasaronObligatoria}")
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
