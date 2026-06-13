package pe.dcs.app.features.user.ministry_user.request;

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
public class MinistryAssignmentRequest {

    private UUID ministryId;

    private UUID ministryRoleId;

    private LocalDate startDate;

    private LocalDate endDate;

    private String reason;

    private String observation;
}