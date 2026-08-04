package pe.dcs.app.util.enums.space_reservation;

/**
 * Origen de una reserva de espacio (SpaceReservation). Cuando es
 * distinto de OTHER, {@code sourceId} referencia el registro del
 * módulo correspondiente (Event, SmallGroup o BibleClass) SIN llave
 * foránea real — se guarda solo el UUID para no acoplar el paquete
 * de Reservas de Espacios con esos otros módulos (ver
 * SpaceReservationServiceImpl). OTHER es la única opción con motivo
 * en texto libre (purpose) y sin sourceId.
 */
public enum ReservationSourceType {
    EVENT,
    SMALL_GROUP,
    BIBLE_CLASS,
    OTHER
}
