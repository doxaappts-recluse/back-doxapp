package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.events.EventStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "events",
        indexes = {
                @Index(
                        name = "idx_event_organization",
                        columnList = "organization_id"
                ),
                @Index(
                        name = "idx_event_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_event_name",
                        columnList = "name"
                ),
                @Index(
                        name = "idx_event_start_date_time",
                        columnList = "start_date_time"
                ),
                @Index(
                        name = "idx_event_end_date_time",
                        columnList = "end_date_time"
                )
        }
)
@Getter
@Setter
public class Event extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(length = 3000)
    private String description;

    @Column(name = "start_date_time", nullable = false)
    private LocalDateTime startDateTime;

    @Column(name = "end_date_time", nullable = false)
    private LocalDateTime endDateTime;

    private String location;

    private Integer capacity;

    private Integer goal;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "expected_budget")
    private BigDecimal expectedBudget;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @Column(name = "template_path")
    private String templatePath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "organization_id",
            nullable = false
    )
    private Organization organization;

    @OneToMany(mappedBy = "event")
    private List<EventRegistration> registrations;

    @OneToMany(mappedBy = "event")
    private List<EventFinance> finances;
}