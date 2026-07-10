package pe.dcs.app.util.auditable;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public abstract class AuditableResponse {

    private UUID createdById;
    private String createdBy;

    private Instant createdAt;

    private UUID updatedById;
    private String updatedBy;

    private Instant updatedAt;
}