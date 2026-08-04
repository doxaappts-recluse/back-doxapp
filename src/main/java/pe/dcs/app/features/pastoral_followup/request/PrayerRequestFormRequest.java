package pe.dcs.app.features.pastoral_followup.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.followup.PrayerRequestStatus;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class PrayerRequestFormRequest {

    private LocalDate requestDate;

    private String description;

    private PrayerRequestStatus status;

    private boolean confidential;

    private String answeredNotes;

    /**
     * Igual criterio que FollowUpContactFormRequest.branchId.
     */
    private UUID branchId;
}
