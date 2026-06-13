package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.Auditable;
import pe.dcs.app.util.enums.events.RegistrationCategory;
import pe.dcs.app.util.enums.events.RegistrationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "event_registrations",
        indexes = {
                @Index(
                        name = "idx_registration_event",
                        columnList = "event_id"
                ),
                @Index(
                        name = "idx_registration_user",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_registration_category",
                        columnList = "category"
                ),
                @Index(
                        name = "idx_registration_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_registration_name",
                        columnList = "name"
                ),
                @Index(
                        name = "idx_registration_lastname",
                        columnList = "lastname"
                ),
                @Index(
                        name = "idx_registration_phone",
                        columnList = "phone"
                ),
                @Index(
                        name = "idx_registration_event_status",
                        columnList = "event_id,status"
                )
        }
)
@Getter
@Setter
public class EventRegistration extends Auditable {

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

    /**
     * Miembro de la iglesia.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegistrationCategory category;

    /**
     * Invitado externo.
     */
    private String name;
    private String lastname;

    private String phone;

    private String email;

    @Column(name = "regular_price")
    private BigDecimal regularPrice;

    private BigDecimal discount;

    @Column(name = "final_price")
    private BigDecimal finalPrice;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegistrationStatus status;

    private String observations;

    @Column(
            name = "qr_token",
            unique = true,
            nullable = false,
            updatable = false,
            length = 36
    )
    private String qrToken;

    @Column(name = "qr_used")
    private Boolean qrUsed = false;

    @Column(name = "qr_used_at")
    private LocalDateTime qrUsedAt;

    @OneToOne(
            mappedBy = "registration",
            fetch = FetchType.LAZY
    )
    private EventAttendance attendance;
}