package pe.dcs.app.util.enums.hr;

/** Tipo de una {@link pe.dcs.app.entity.LeaveRequest} (vacaciones/permiso). */
public enum HrLeaveType {
    VACATION,               // Vacaciones
    PERSONAL_PERMIT,        // Permiso personal
    SICK_LEAVE,             // Licencia médica
    MATERNITY_PATERNITY,    // Licencia por maternidad/paternidad
    UNPAID_LEAVE,           // Licencia sin goce de haber
    OTHER
}
