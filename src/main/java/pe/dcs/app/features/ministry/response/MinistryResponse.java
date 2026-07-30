package pe.dcs.app.features.ministry.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Getter
@Setter
public class MinistryResponse extends AuditableResponse {
    private UUID id;
    private String name;
    private String description;
    private StatusType status;
}