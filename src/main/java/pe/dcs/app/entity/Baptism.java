package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.Auditable;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "baptisms",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_baptism_user",
                        columnNames = "user_id"
                )
        },
        indexes = {
                @Index(name = "idx_baptism_date", columnList = "baptism_date"),
                @Index(name = "idx_baptism_church", columnList = "church_name"),
                @Index(name = "idx_baptism_verified", columnList = "verified")
        }
)
@Getter
@Setter
public class Baptism extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            unique = true
    )
    private User user;

    @Column(name = "baptism_date")
    private LocalDate baptismDate;

    @Column(name = "church_name")
    private String churchName;

    @Column(name = "pastor_name")
    private String pastorName;

    @Column(name = "city")
    private String city;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "observations", length = 1000)
    private String observations;
}