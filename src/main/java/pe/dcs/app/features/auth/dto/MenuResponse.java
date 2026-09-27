package pe.dcs.app.features.auth.dto;

import java.util.List;

/** {@code limited}: ORG_ADMIN sin contrato ACTIVE (modo limitado: solo lectura y solicitar renovación). */
public record MenuResponse(List<MenuItem> items, boolean limited) {
}
