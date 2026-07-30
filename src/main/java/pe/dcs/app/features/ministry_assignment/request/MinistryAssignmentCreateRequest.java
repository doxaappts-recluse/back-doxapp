package pe.dcs.app.features.ministry_assignment.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class MinistryAssignmentCreateRequest {

    private UUID ministryId;

    private UUID ministryRoleId;

    private LocalDate startDate;

    private LocalDate endDate;

    private String reason;

    private String observation;

}
