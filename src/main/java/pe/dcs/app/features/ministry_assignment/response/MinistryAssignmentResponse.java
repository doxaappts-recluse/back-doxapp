package pe.dcs.app.features.ministry_assignment.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class MinistryAssignmentResponse {

    private UUID assignmentId;

    private MinistryAssignmentRefResponse ministry;

    private MinistryAssignmentRefResponse role;

    private LocalDate startDate;

    private LocalDate endDate;

    private String reason;

    private String observation;

    /**
     * true si el periodo sigue vigente (endDate == null).
     */
    private boolean current;

}
