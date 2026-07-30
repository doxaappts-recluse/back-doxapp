package pe.dcs.app.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.events.EventScope;
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "template_config", columnDefinition = "jsonb")
    private JsonNode templateConfig;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "organization_id",
            nullable = false
    )
    private Organization organization;

    /**
     * Controla únicamente VISIBILIDAD para otras sedes (listar,
     * inscribir, agregar movimientos financieros):
     *
     * ORGANIZATION: cualquier sede de la organización puede ver el
     * evento, inscribir gente y agregar movimientos financieros.
     *
     * BRANCH: solo lo ve/usa la sede coordinadora (branch).
     *
     * NO decide quién GESTIONA el evento (editar, publicar,
     * cancelar, ver dashboard/reportes/asistencia, aprobar
     * finanzas): eso siempre es la sede coordinadora + el org
     * admin, sin importar este valor. Ver
     * {@link #isOwnedByBranch(UUID)}.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventScope scope = EventScope.ORGANIZATION;

    /**
     * Sede coordinadora/anfitriona del evento. Siempre requerida:
     * un branch admin queda ligado automáticamente a la suya; un
     * org admin debe elegirla explícitamente. Es la sede que,
     * junto al org admin, siempre gestiona el evento (editar,
     * publicar/cancelar, dashboard, reportes, asistencia, aprobar
     * finanzas) — independientemente del scope, que solo amplía
     * quién puede VER el evento e inscribir/aportar finanzas.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "branch_id",
            nullable = false
    )
    private Branch branch;

    @OneToMany(mappedBy = "event")
    private List<EventRegistration> registrations;

    @OneToMany(mappedBy = "event")
    private List<EventFinance> finances;

    public boolean isOrganizationScope() {
        return scope == EventScope.ORGANIZATION;
    }

    public boolean isBranchScope() {
        return scope == EventScope.BRANCH;
    }

    /**
     * ¿La sede dada es la coordinadora/anfitriona de este evento?
     * Determina quién GESTIONA el evento (además del org admin),
     * sin importar el scope.
     */
    public boolean isOwnedByBranch(UUID branchId) {
        return branch != null
                && branchId != null
                && branch.getId().equals(branchId);
    }
}