package pe.dcs.app.features.contract.dto;

import java.time.LocalDate;

/** Fecha de inicio del nuevo periodo (única fórmula, calculada en el servidor). */
public record NextStartResponse(String type, LocalDate startDate, LocalDate currentEndDate, boolean startsImmediately,
                                boolean inPlace) {
}
