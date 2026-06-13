package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.Auditable;
import pe.dcs.app.util.enums.events.EventFinanceStatus;
import pe.dcs.app.util.enums.events.EventFinanceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "event_finances",
        indexes = {
                @Index(
                        name = "idx_finance_event",
                        columnList = "event_id"
                ),
                @Index(
                        name = "idx_finance_type",
                        columnList = "type"
                ),
                @Index(
                        name = "idx_finance_date",
                        columnList = "transaction_date"
                )
        }
)
@Getter
@Setter
public class EventFinance extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "event_id",
            nullable = false
    )
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventFinanceType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventFinanceStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedByUser;

    private LocalDateTime approvedAt;

    private String rejectionReason;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "transaction_date")
    private LocalDate transactionDate;

    private String observations;
}