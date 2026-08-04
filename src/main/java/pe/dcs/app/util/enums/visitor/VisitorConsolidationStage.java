package pe.dcs.app.util.enums.visitor;

/**
 * Etapa de consolidación de un visitante (ver
 * {@link pe.dcs.app.entity.Visitor}). CONVERTED es el único estado
 * terminal positivo — se alcanza al crear la Membership vinculada
 * (ver VisitorServiceImpl.convertToMember); LOST es terminal
 * negativo (dejó de asistir / no se pudo consolidar).
 */
public enum VisitorConsolidationStage {

    NEW,            // Recién llegado, sin seguimiento aún
    IN_FOLLOWUP,    // En proceso de seguimiento/consolidación
    INTEGRATED,     // Asiste regularmente, aún no es miembro
    CONVERTED,      // Se convirtió en miembro (ver Membership vinculada)
    LOST            // Dejó de asistir / no se pudo consolidar

}
