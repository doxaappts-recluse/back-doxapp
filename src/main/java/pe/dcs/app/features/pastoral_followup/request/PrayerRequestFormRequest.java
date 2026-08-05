package pe.dcs.app.features.pastoral_followup.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.followup.PrayerRequestStatus;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class PrayerRequestFormRequest {

    @NotNull(message = "{error.fechaPeticionObligatoria}")
    private LocalDate requestDate;

    @NotBlank(message = "{error.descripcionPeticionObligatoria}")
    private String description;

    @NotNull(message = "{error.estadoPeticionObligatorio}")
    private PrayerRequestStatus status;

    private boolean confidential;

    private String answeredNotes;

    /**
     * Igual criterio que FollowUpContactFormRequest.branchId.
     */
    private UUID branchId;
}
