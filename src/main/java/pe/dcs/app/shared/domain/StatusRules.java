package pe.dcs.app.shared.domain;

import org.springframework.http.HttpStatus;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;

/**
 * Núcleo 01 §7 · Statusable: ACTIVE ⇄ INACTIVE (motivo obligatorio al inactivar); LOCKED solo por seguridad.
 * Reglas de transición en un solo lugar para no duplicarlas en cada módulo.
 */
public final class StatusRules {

    private StatusRules() {
    }

    /** Valida el cambio de estado y el motivo. No modifica nada. */
    public static void assertTransition(StatusType from, StatusType to, String reason) {
        if (from == to) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, from);
        }
        if (to == StatusType.LOCKED) {
            // LOCKED solo lo aplica la capa de seguridad, nunca un usuario desde un módulo
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, from);
        }
        if (to == StatusType.INACTIVE && (reason == null || reason.isBlank())) {
            throw new Exceptions("error.common.reasonRequired", HttpStatus.BAD_REQUEST);
        }
    }
}
