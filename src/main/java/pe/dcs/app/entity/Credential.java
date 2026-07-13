package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Entity
@Table(name="credentials")
@Getter
@Setter
public class Credential extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(
            nullable=false,
            unique=true
    )
    private String username;

    @Column(nullable=false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private StatusType status;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name="person_id"
    )
    private Person person;

    public boolean canLogin(){
        return status == StatusType.ACTIVE;
    }

    public boolean isLocked(){
        return status == StatusType.LOCKED;
    }

    public boolean isInactive(){
        return status == StatusType.INACTIVE;
    }

}