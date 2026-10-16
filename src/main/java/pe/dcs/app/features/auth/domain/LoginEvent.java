package pe.dcs.app.features.auth.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.features.access.domain.ActorType;

import java.time.Instant;
import java.util.UUID;

/** Evento de autenticación (éxitos, fallos, bloqueos, reúso de refresh). Alimenta auditoría de seguridad y M01-N1. */
@Getter
@Setter
@Entity
@Table(name = "login_event")
public class LoginEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "at", nullable = false)
    private Instant at;

    @Column(name = "credential_id")
    private UUID credentialId;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", length = 10)
    private ActorType actorType;

    @Column(name = "username", length = 160)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 30)
    private LoginResult result;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "detail", length = 255)
    private String detail;
}
