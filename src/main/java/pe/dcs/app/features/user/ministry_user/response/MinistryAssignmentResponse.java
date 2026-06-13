package pe.dcs.app.features.user.ministry_user.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MinistryAssignmentResponse {

    private UUID assignmentId;

    private UUID roleId;

    private String roleName;

    private LocalDate startDate;

    private LocalDate endDate;

    private String reason;

    private String observation;

    private Boolean current;
}