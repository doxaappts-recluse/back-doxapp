package pe.dcs.app.util.enums.membership;

/**
 * Condición de la persona respecto a la iglesia. No confundir
 * con MembershipStatus (si la membresía está activa o no):
 * esto es "qué tipo de asistente/miembro es" mientras dure.
 *
 * VISITOR se retiró de acá: un visitante ya no es un tipo de
 * Membership, es un registro propio (ver entity Visitor /
 * features.visitor) — evita mezclar "todavía no es miembro" con
 * "tiene un período de membresía". Al convertirse en miembro
 * (VisitorServiceImpl.convertToMember) se abre recién ahí una
 * Membership con reason=MEMBERSHIP.
 */
public enum MembershipReason {

    MEMBERSHIP, // Miembro
    NEW         // Nuevo asistente (aún no formalizado como miembro pleno)

}
