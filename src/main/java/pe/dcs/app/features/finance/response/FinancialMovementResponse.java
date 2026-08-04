package pe.dcs.app.features.finance.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;
import pe.dcs.app.util.enums.finance.FinancialMovementCategory;
import pe.dcs.app.util.enums.finance.FinancialMovementPaymentMethod;
import pe.dcs.app.util.enums.finance.FinancialMovementStatus;
import pe.dcs.app.util.enums.finance.FinancialMovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class FinancialMovementResponse extends AuditableResponse {

    private UUID id;

    private UUID branchId;
    private String branchName;

    private FinancialMovementType type;
    private FinancialMovementCategory category;
    private FinancialMovementStatus status;

    private UUID personId;
    private String personName;
    private String personLastname;

    private UUID fundId;
    private String fundName;

    private FinancialMovementPaymentMethod paymentMethod;

    private String concept;
    private BigDecimal amount;
    private LocalDate movementDate;
    private String observations;

    private UUID createdByUserId;
    private String createdByUserName;

    private UUID approvedByUserId;
    private String approvedByUserName;

    private Instant approvedAt;

    /*
     * "Actualizado por" ya se expone heredado de AuditableResponse
     * (updatedBy/updatedById), poblado automáticamente por
     * BaseMapper.mapAudit() cuando showAudit=true — no hace falta
     * un campo nuevo acá, solo agregarlo como columna en el front.
     */

    /**
     * ¿Puede quien llama aprobar/rechazar este movimiento? Solo
     * org admin de la organización o branch admin de esa sede
     * puntual (ver FinancialAccessGuard.canApprove) — nunca un org
     * user delegado, ni siquiera el que lo creó (ver `owner`).
     */
    private boolean canManage;

    /**
     * ¿Quien llama creó este movimiento? Habilita editar mientras
     * esté PENDING (junto con canManage), pero no aprobar/rechazar.
     */
    private boolean owner;
}
