package pe.dcs.app.features.event.response.finance;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.events.EventFinanceStatus;
import pe.dcs.app.util.enums.events.EventFinanceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class EventFinanceResponse {

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

    private LocalDateTime approvedAt;

    private String rejectionReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}