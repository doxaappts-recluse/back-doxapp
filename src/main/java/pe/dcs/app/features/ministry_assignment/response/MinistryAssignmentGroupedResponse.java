package pe.dcs.app.features.ministry_assignment.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class MinistryAssignmentGroupedResponse {

    private UUID ministryId;

    private String ministryName;

    private List<MinistryAssignmentGroupedItemResponse> assignments;

}
