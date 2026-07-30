package pe.dcs.app.features.ministry_assignment.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class MinistryAssignmentUpdateRequest {

    private LocalDate startDate;

    private LocalDate endDate;

    private String reason;

    private String observation;

}
