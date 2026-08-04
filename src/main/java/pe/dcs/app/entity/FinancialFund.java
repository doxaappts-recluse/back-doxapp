package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

/**
 * Fondo dentro de la organización (Fondo General, Construcción,
 * Misiones, Ayuda Social, etc.). Cada iglesia define los suyos —
 * es un catálogo administrado por el propio org admin, no un
 * enum fijo, porque el set de fondos varía mucho de una
 * organización a otra. Un {@link FinancialMovement} puede
 * asociarse opcionalmente a un fondo para saber a qué "bolsillo"
 * pertenece el dinero, independientemente de su categoría
 * (diezmo/ofrenda/donación/gasto).
 */
@Entity
@Table(
        name = "financial_funds",
        indexes = {
                @Index(
                        name = "idx_financial_fund_organization",
                        columnList = "organization_id"
                )
        }
)
@Getter
@Setter
public class FinancialFund extends Auditable {

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

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType status;
}
