package pe.dcs.app.util.enums.events;

/**
 * Alcance de visibilidad/gestión de un evento.
 *
 * ORGANIZATION: visible y gestionable por toda la organización
 * (cualquier sede puede inscribir gente; solo el org admin
 * aprueba/rechaza sus movimientos financieros).
 *
 * BRANCH: visible y gestionable solo por la sede dueña
 * (branch) además del org admin; ninguna otra sede lo ve.
 */
public enum EventScope {
    ORGANIZATION,
    BRANCH
}
