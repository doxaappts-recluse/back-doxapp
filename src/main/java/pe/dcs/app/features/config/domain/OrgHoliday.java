package pe.dcs.app.features.config.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/** Feriado de la organización (calendario laboral). Alimenta M15, M17 y M20. */
@Getter
@Setter
@Entity
@Table(name = "org_holiday")
public class OrgHoliday {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate date;

    @Column(name = "name", nullable = false, length = 100)
    private String name;
}
