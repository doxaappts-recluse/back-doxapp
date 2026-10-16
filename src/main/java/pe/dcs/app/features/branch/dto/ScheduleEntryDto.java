package pe.dcs.app.features.branch.dto;

import jakarta.validation.constraints.Size;

/** Un horario público de la sede: día (MON..SUN), hora de inicio y fin "HH:mm" y una etiqueta opcional ("Culto dominical"). */
public record ScheduleEntryDto(
        @Size(max = 3, message = "error.common.tooLong") String day,
        @Size(max = 5, message = "error.common.tooLong") String from,
        @Size(max = 5, message = "error.common.tooLong") String to,
        @Size(max = 60, message = "error.common.tooLong") String label
) {
}
