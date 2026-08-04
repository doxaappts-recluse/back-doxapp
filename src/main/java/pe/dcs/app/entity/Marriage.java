package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Registro de matrimonio realizado en la iglesia. A diferencia de
 * {@link Baptism} (1:1 con una Person ya existente), acá los dos
 * cónyuges pueden o no ser Person del sistema: si se encuentran por
 * DNI se guarda el vínculo (spouse1Person/spouse2Person), si no,
 * queda solo el nombre en texto libre — el público general que se
 * casa en la iglesia no tiene por qué existir como Person.
 */
@Entity
@Table(
        name = "marriages",
        indexes = {
                @Index(name = "idx_marriage_date", columnList = "marriage_date"),
                @Index(name = "idx_marriage_church", columnList = "church_name"),
                @Index(name = "idx_marriage_branch", columnList = "branch_id")
        }
)
@Getter
@Setter
public class Marriage extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "spouse1_name", nullable = false)
    private String spouse1Name;

    @Column(name = "spouse1_dni")
    private String spouse1Dni;

    /**
     * Solo se setea si el cónyuge se encontró por DNI dentro de la
     * organización. Ver MarriageServiceImpl — el auto-update de
     * maritalStatus solo aplica si, además, tiene una membresía
     * activa (no alcanza con existir como Person).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spouse1_person_id")
    private Person spouse1Person;

    @Column(name = "spouse2_name", nullable = false)
    private String spouse2Name;

    @Column(name = "spouse2_dni")
    private String spouse2Dni;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spouse2_person_id")
    private Person spouse2Person;

    @Column(name = "marriage_date", nullable = false)
    private LocalDate marriageDate;

    @Column(name = "church_name", nullable = false)
    private String churchName;

    @Column(name = "pastor_name")
    private String pastorName;

    @Column(name = "city")
    private String city;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "observations", length = 1000)
    private String observations;

    /**
     * Tarifa cobrada por el matrimonio, opcional (queda null si no
     * se cobró nada). Si viene informada, MarriageServiceImpl crea
     * automáticamente el FinancialMovement equivalente (categoría
     * SERVICE_FEE) y lo enlaza en financialMovement — la plata
     * institucional vive en un solo lugar (Movimientos), no
     * duplicada acá.
     */
    @Column(name = "fee_amount", precision = 12, scale = 2)
    private BigDecimal feeAmount;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "financial_movement_id")
    private FinancialMovement financialMovement;

    /**
     * Sede desde la que se creó este registro. Mismo propósito que
     * Baptism.branch/Membership.branch.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;
}
