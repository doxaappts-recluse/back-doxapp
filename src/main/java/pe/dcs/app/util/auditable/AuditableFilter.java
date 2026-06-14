package pe.dcs.app.util.auditable;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class AuditableFilter {

    private LocalDateTime createdAtFrom;
    private LocalDateTime createdAtTo;

    private LocalDateTime updatedAtFrom;
    private LocalDateTime updatedAtTo;

    private UUID createdById;
    private UUID updatedById;

    private String createdBy;
    private String updatedBy;
}