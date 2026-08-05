package pe.dcs.app.features.space_reservation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.space_reservation.ReservationSourceType;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class SpaceReservationFormRequest {

    @NotNull(message = "{error.debeSeleccionarEspacioReservar}")
    private UUID spaceId;

    @NotNull(message = "{error.tipoVinculoReservaObligatorio}")
    private ReservationSourceType sourceType;

    /** Obligatorio si sourceType != OTHER. Sin validación de existencia en este módulo — ver SpaceReservation. */
    private UUID sourceId;

    /**
     * Motivo/actividad. Si sourceType=OTHER se escribe libremente; si
     * está vinculado a otro módulo, el frontend lo autocompleta con
     * el nombre del registro elegido (snapshot) — el backend solo
     * exige que no venga vacío, no lo deriva por su cuenta (evita
     * acoplar este módulo con Events/SmallGroup/BibleAcademy).
     */
    @NotBlank(message = "{error.motivoActividadReservaObligatorio}")
    private String purpose;

    /** Setear solo si el responsable se encontró por DNI. Si es null, queda solo requesterName en texto libre. */
    private UUID requesterPersonId;
    private String requesterName;
    private String requesterDni;

    @NotNull(message = "{error.fechaHoraInicioFinSonObligatorias}")
    private LocalDateTime startDateTime;
    @NotNull(message = "{error.fechaHoraInicioFinSonObligatorias}")
    private LocalDateTime endDateTime;

    private String notes;
}
