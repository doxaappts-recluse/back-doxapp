package pe.dcs.app.features.pastoral_followup.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;
import pe.dcs.app.util.enums.followup.PrayerRequestStatus;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class PrayerRequestResponse extends AuditableResponse {

    private UUID id;

    private UUID personId;

    private LocalDate requestDate;

    private String description;

    private PrayerRequestStatus status;

    private boolean confidential;

    private String answeredNotes;

    private UUID branchId;
    private String branchName;
}
