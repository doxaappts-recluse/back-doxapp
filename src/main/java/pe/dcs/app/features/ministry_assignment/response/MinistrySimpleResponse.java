package pe.dcs.app.features.ministry_assignment.response;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class MinistrySimpleResponse {

    private UUID ministryId;

    private String ministryName;

    public MinistrySimpleResponse() {
    }

    public MinistrySimpleResponse(UUID ministryId, String ministryName) {
        this.ministryId = ministryId;
        this.ministryName = ministryName;
    }

}
