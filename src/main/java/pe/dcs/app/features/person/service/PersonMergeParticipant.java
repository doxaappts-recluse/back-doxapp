package pe.dcs.app.features.person.service;

import java.util.UUID;

/**
 * Contrato para que otros módulos (visitantes, membresía, asistencia, finanzas…) participen en la fusión y la anonimización de
 * personas sin que Personas conozca sus tablas. Cada módulo publica un bean que implemente esta interfaz; Personas los recorre.
 * Todos los métodos se ejecutan dentro de la transacción de la operación.
 */
public interface PersonMergeParticipant {

    /** Clave del impacto en la vista previa de la fusión (el front la traduce como {@code person.merge.impact.<key>}). */
    String key();

    /** Cuántos registros de la persona duplicada pasarán a la persona que se conserva. */
    int count(UUID orgId, UUID sourceId);

    /** Clave de mensaje (error.*) si algo impide fusionar, o null si no hay problema. */
    default String blocker(UUID orgId, UUID sourceId, UUID targetId) {
        return null;
    }

    /** Traslada los registros de {@code sourceId} a {@code targetId}. */
    void migrate(UUID orgId, UUID sourceId, UUID targetId);

    /** Borra la identidad que el módulo guarde de la persona (textos libres, notas…) conservando los agregados. */
    default void anonymize(UUID orgId, UUID personId) {
    }
}
