package pe.dcs.app.util.enums.hr;

/**
 * Tipo de contrato laboral de un {@link pe.dcs.app.entity.StaffMember}.
 * Puramente informativo — no cambia ninguna regla de negocio, solo
 * documenta bajo qué modalidad está el trabajador.
 */
public enum HrContractType {
    PLANILLA_INDEFINIDO,
    PLANILLA_PLAZO_FIJO,
    RECIBO_HONORARIOS,
    PRACTICAS,
    OTRO
}
