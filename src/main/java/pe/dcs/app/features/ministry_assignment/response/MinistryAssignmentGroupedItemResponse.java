package pe.dcs.app.features.ministry_assignment.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class MinistryAssignmentGroupedItemResponse {

    private UUID assignmentId;

    private String role;

    private LocalDate startDate;

    private LocalDate endDate;

    private String reason;

    private String observation;

    private boolean current;

}
