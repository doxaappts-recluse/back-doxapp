package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.finance.FinancialMovementCategory;
import pe.dcs.app.util.enums.finance.FinancialMovementPaymentMethod;
import pe.dcs.app.util.enums.finance.FinancialMovementStatus;
import pe.dcs.app.util.enums.finance.FinancialMovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Movimiento financiero institucional (diezmo, ofrenda, donación,
 * ingreso general o gasto) de una sede. Es el módulo "Finanzas
 * Institucionales" del plan de precios — no confundir con
 * {@link EventFinance}, que es la finanza puntual de un evento.
 *
 * Flujo de aprobación igual a EventFinance: quien crea con
 * autoridad sobre la sede (org admin/branch admin) queda APPROVED
 * de una vez; un org user delegado (permiso CREATE del módulo)
 * queda en PENDING hasta que un admin lo apruebe o rechace.
 */
@Entity
@Table(
        name = "financial_movements",
        indexes = {
                @Index(
                        name = "idx_financial_movement_branch",
                        columnList = "branch_id"
                ),
                @Index(
                        name = "idx_financial_movement_type",
                        columnList = "type"
                ),
                @Index(
                        name = "idx_financial_movement_category",
                        columnList = "category"
                ),
                @Index(
                        name = "idx_financial_movement_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_financial_movement_date",
                        columnList = "movement_date"
                )
        }
)
@Getter
@Setter
public class FinancialMovement extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "organization_id",
            nullable = false
    )
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "branch_id",
            nullable = false
    )
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FinancialMovementType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FinancialMovementCategory category;

    /**
     * Donante, opcional. Solo aplica a categorías de ingreso
     * (diezmo/ofrenda/donación); permite historial de aportes por
     * persona. Si no se selecciona, el movimiento queda anónimo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    /**
     * Fondo al que pertenece el dinero (Fondo General, Construcción,
     * Misiones, etc.), opcional e independiente de la categoría:
     * la categoría dice CÓMO entró/salió el dinero (diezmo, gasto),
     * el fondo dice A QUÉ "bolsillo" pertenece. Ver {@link FinancialFund}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id")
    private FinancialFund fund;

    @Enumerated(EnumType.STRING)
    private FinancialMovementPaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FinancialMovementStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private Person createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private Person approvedByUser;

    private Instant approvedAt;

    @Column(nullable = false)
    private String concept;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "movement_date", nullable = false)
    private LocalDate movementDate;

    /**
     * Notas generales al crear/editar, o el motivo cuando el
     * movimiento queda REJECTED (mismo uso dual que
     * {@link EventFinance#getObservations()}).
     */
    private String observations;
}
