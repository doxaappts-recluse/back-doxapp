package pe.dcs.app.features.pastoral_followup.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;
import pe.dcs.app.util.enums.followup.FollowUpContactMethod;
import pe.dcs.app.util.enums.followup.FollowUpContactResult;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class FollowUpContactResponse extends AuditableResponse {

    private UUID id;

    private UUID personId;

    private LocalDate contactDate;

    private FollowUpContactMethod contactMethod;

    private FollowUpContactResult result;

    private String notes;

    private UUID branchId;
    private String branchName;
}
