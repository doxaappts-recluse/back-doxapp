package pe.dcs.app.features.transfer.service;

import java.util.UUID;

/**
 * Lo que otro módulo cierra o mueve cuando una persona cambia de sede (membresía M08, grupos M10, ministerios M11…). Cada módulo
 * registra un bean; la vista previa suma sus {@link #count} y la ejecución los llama dentro de la MISMA transacción del traslado
 * ([V8] todo o nada): si uno lanza, no queda nada a medias. Los datos históricos y las finanzas no se tocan.
 */
public interface BranchTransferParticipant {

    /** Clave de la línea de impacto; el front la traduce (transfer.impact.&lt;key&gt;). */
    String key();

    /** Cuántos registros de la sede origen se verán afectados. */
    int count(UUID orgId, UUID personId, UUID fromBranchId);

    /** Aplica el cambio. La persona todavía figura en la sede origen o ya está en el destino según el orden; usar los ids recibidos. */
    void execute(Context ctx);

    record Context(UUID orgId, UUID personId, UUID fromBranchId, UUID toBranchId, UUID actorPersonId, boolean moveMembership, boolean endGroups,
                   boolean endMinistries) {
    }
}
