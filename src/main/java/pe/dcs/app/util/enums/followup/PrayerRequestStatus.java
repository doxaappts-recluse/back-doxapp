package pe.dcs.app.util.enums.followup;

/**
 * Estado de una petición de oración (ver
 * {@link pe.dcs.app.entity.PrayerRequest}).
 */
public enum PrayerRequestStatus {

    OPEN,           // Recién registrada, sin seguimiento aún
    IN_PROGRESS,    // En seguimiento / orando por ella
    ANSWERED,       // Respondida (testimonio positivo)
    CLOSED          // Cerrada sin más seguimiento

}
