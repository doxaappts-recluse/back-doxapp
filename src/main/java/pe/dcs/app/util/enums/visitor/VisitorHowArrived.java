package pe.dcs.app.util.enums.visitor;

/**
 * Cómo llegó un visitante a la iglesia por primera vez (ver
 * {@link pe.dcs.app.entity.Visitor}).
 */
public enum VisitorHowArrived {

    INVITED_BY_MEMBER,  // Invitado por un miembro (ver Visitor.invitedBy)
    SOCIAL_MEDIA,       // Redes sociales
    EVENT,              // Llegó por un evento puntual
    WALK_IN,            // Llegó por cuenta propia, sin invitación
    OTHER               // Otro medio

}
