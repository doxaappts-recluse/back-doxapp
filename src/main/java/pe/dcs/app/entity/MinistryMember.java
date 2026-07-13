package pe.dcs.app.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.enums.StatusType;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name="ministry_members")
public class MinistryMember {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne
    @JoinColumn(name="person_id")
    private Person person;

    @ManyToOne
    @JoinColumn(name="ministry_role_id")
    private MinistryRole ministryRole;

    private LocalDate startDate;

    private LocalDate endDate;

    private StatusType active;

}