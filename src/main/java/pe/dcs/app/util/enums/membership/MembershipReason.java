package pe.dcs.app.util.enums.membership;

/**
 * Condición de la persona respecto a la iglesia. No confundir
 * con MembershipStatus (si la membresía está activa o no):
 * esto es "qué tipo de asistente/miembro es" mientras dure.
 */
public enum MembershipReason {

    MEMBERSHIP, // Miembro
    NEW,        // Nuevo asistente
    VISITOR     // Visitante

}
