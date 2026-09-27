package pe.dcs.app.features.support.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Mensaje del hilo. internal = nota solo de plataforma (V5: nunca se serializa hacia la organización). */
@Getter
@Setter
@Entity
@Table(name = "support_message")
public class SupportMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId;

    /** MESSAGE | EVENT */
    @Column(name = "kind", nullable = false, length = 10)
    private String kind = "MESSAGE";

    /** STAFF | PERSON | SYSTEM */
    @Column(name = "author_type", nullable = false, length = 10)
    private String authorType;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "author_name", nullable = false, length = 170)
    private String authorName;

    @Column(name = "body", nullable = false, length = 4000)
    private String body;

    @Column(name = "internal", nullable = false)
    private boolean internal;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
