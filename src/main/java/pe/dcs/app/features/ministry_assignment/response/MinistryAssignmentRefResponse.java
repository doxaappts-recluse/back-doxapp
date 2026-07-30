package pe.dcs.app.features.ministry_assignment.response;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Referencia mínima {id,name}, reutilizada tanto para el
 * ministerio como para el rol dentro de MinistryAssignmentResponse.
 */
@Getter
@Setter
public class MinistryAssignmentRefResponse {

    private UUID id;

    private String name;

    public MinistryAssignmentRefResponse() {
    }

    public MinistryAssignmentRefResponse(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

}
