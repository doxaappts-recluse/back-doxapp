package pe.dcs.app.features.organization.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.shared.domain.Auditable;
import pe.dcs.app.shared.vo.Address;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** M02 · Organización (tenant): datos legales, ubicación, regionalización y ciclo de vida. La marca vive en {@link OrganizationBranding}. */
@Getter
@Setter
@Entity
@Table(name = "organization")
public class Organization extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "legal_name", length = 160)
    private String legalName;

    @Column(name = "tax_id", length = 11)
    private String taxId;

    @Column(name = "slug", nullable = false, length = 30)
    private String slug;

    @Column(name = "country", nullable = false, length = 2)
    private String country = "PE";

    @Column(name = "email", length = 160)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "founded_date")
    private LocalDate foundedDate;

    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone = "America/Lima";

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "PEN";

    @Column(name = "default_language", nullable = false, length = 2)
    private String defaultLanguage = "es";

    @Column(name = "fiscal_year_start_month", nullable = false)
    private Short fiscalYearStartMonth = 1;

    /** M23 · formato de fecha para mostrar (dd/MM/yyyy, MM/dd/yyyy, yyyy-MM-dd). */
    @Column(name = "date_format", nullable = false, length = 12)
    private String dateFormat = "dd/MM/yyyy";

    /** M23 · días laborables separados por coma (MON..SUN). */
    @Column(name = "working_days", nullable = false, length = 30)
    private String workingDays = "MON,TUE,WED,THU,FRI";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrganizationStatus status = OrganizationStatus.DRAFT;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Embedded
    private Address address = new Address();

    /** Motivo de la suspensión vigente (solo SUSPENDED). */
    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    /** Hasta cuándo se conservan los datos de una organización CERRADA (90 días); luego se anonimizan. */
    @Column(name = "retention_until")
    private LocalDate retentionUntil;
}
