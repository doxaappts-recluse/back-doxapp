package pe.dcs.app.features.event.response.finance;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;
import pe.dcs.app.util.enums.events.EventFinanceStatus;
import pe.dcs.app.util.enums.events.EventFinanceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class EventFinanceResponse extends AuditableResponse {

    private UUID id;

    private UUID eventId;
    private String eventName;

    private EventFinanceType type;
    private EventFinanceStatus status;

    private String description;
    private BigDecimal amount;
    private LocalDate transactionDate;
    private String observations;

    private UUID createdByUserId;
    private String createdByUserName;

    private UUID approvedByUserId;
    private String approvedByUserName;

    private Instant approvedAt;

    private String rejectionReason;

    /**
     * ¿Puede quien llama aprobar/rechazar este movimiento? Es la
     * misma autoridad que gestiona el EVENTO (EventAccessGuard.
     * canManage): org admin, la sede coordinadora, o el org user
     * que creó el evento con EDIT. Deliberadamente NO se le da esta
     * autoridad a quien creó el propio movimiento financiero (ver
     * `owner`) — permitir la autoaprobación anularía el sentido del
     * estado PENDING como control.
     */
    private boolean canManage;

    /**
     * ¿Quien llama creó este movimiento puntual? Habilita editar
     * (junto con canManage) pero NO aprobar/rechazar.
     */
    private boolean owner;
}