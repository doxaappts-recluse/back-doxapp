package pe.dcs.app.features.finance.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class FinancialCashRegisterResponse extends AuditableResponse {

    private UUID id;

    private UUID organizationId;

    private UUID branchId;
    private String branchName;

    private LocalDate registerDate;

    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal expectedBalance;
    private BigDecimal difference;

    private String status;

    private UUID openedByUserId;
    private String openedByUserName;
    private Instant openedAt;

    private UUID closedByUserId;
    private String closedByUserName;
    private Instant closedAt;

    private String notes;

    /**
     * ¿Puede quien llama cerrar esta caja? (ver
     * FinancialCashRegisterAccessGuard.canClose) — el front gatea el
     * botón "Cerrar caja" con esto, igual criterio que
     * FinancialMovementResponse.canManage.
     */
    private boolean canManage;
}
