package pe.dcs.app.features.branch.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.dcs.app.shared.domain.TenantScoped;
import pe.dcs.app.shared.vo.Address;
import pe.dcs.app.util.enums.StatusType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M04 · Sede de una organización. Identidad (nombre, código, principal, estado) la gestiona N1; la presentación
 * (nombre visible, logo, contacto, dirección, horarios públicos) la editan también N2/N3 sobre su(s) sede(s).
 */
@Getter
@Setter
@Entity
@Table(name = "branch")
public class Branch extends TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "code", nullable = false, length = 10)
    private String code;

    @Column(name = "is_main", nullable = false)
    private boolean main;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusType status = StatusType.ACTIVE;

    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Column(name = "inactivated_at")
    private Instant inactivatedAt;

    @Embedded
    private Address address = new Address();

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 160)
    private String email;

    @Column(name = "opening_date")
    private LocalDate openingDate;

    /** Zona horaria propia de la sede; nulo = la de la organización. */
    @Column(name = "timezone", length = 50)
    private String timezone;

    @Column(name = "display_name", length = 60)
    private String displayName;

    @Column(name = "logo_key", length = 255)
    private String logoKey;

    @Column(name = "logo_revision", nullable = false)
    private int logoRevision = 1;

    /** Horarios de culto públicos: lista de {day, from, to, label}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "public_schedule", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, String>> publicSchedule = new ArrayList<>();
}
