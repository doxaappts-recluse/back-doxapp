package pe.dcs.app.util.enums;

/**
 * Rol de una Person dentro de un {@link pe.dcs.app.entity.FamilyGroup}
 * (grupo familiar / hogar). HEAD_OF_HOUSEHOLD y SPOUSE se asignan
 * automáticamente al detectar un Matrimonio con al menos un cónyuge
 * vinculado a Person (ver FamilyGroupServiceImpl.syncFromMarriage);
 * CHILD y OTHER se agregan siempre de forma manual — no hay fuente de
 * datos automática para hijos u otros parientes.
 */
public enum FamilyRole {
    HEAD_OF_HOUSEHOLD,
    SPOUSE,
    CHILD,
    OTHER
}
