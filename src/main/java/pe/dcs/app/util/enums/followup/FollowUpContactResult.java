package pe.dcs.app.util.enums.followup;

/**
 * Resultado de un intento de contacto de seguimiento pastoral (ver
 * {@link pe.dcs.app.entity.FollowUpContact}).
 */
public enum FollowUpContactResult {

    CONTACTED,      // Se logró contactar
    NO_ANSWER,      // No contestó / no se encontraba
    RESCHEDULED,    // Se coordinó para otro momento
    REFUSED,        // Rechazó el contacto/seguimiento
    OTHER           // Otro resultado

}
