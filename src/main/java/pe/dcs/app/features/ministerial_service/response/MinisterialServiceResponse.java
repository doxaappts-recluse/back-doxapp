package pe.dcs.app.features.ministerial_service.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;

import java.util.UUID;

@Getter
@Setter
public class MinisterialServiceResponse extends AuditableResponse {

    private UUID id;

    private String name;

    private String lastname;

    private boolean hasMinistry;

}
