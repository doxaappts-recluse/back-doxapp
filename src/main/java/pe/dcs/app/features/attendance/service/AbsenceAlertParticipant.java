package pe.dcs.app.features.attendance.service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Se notifica cuando {@link AttendanceMaintenanceService#absenceAlerts()} abre una nueva alerta de inasistencia (M09),
 * para que otros módulos reaccionen (M12: abre un caso pastoral automático, ver {@code PastoralAbsenceHandler}).
 * Cualquier bean de Spring que la implemente se inyecta solo, como parte de la lista que recibe
 * {@link AttendanceMaintenanceService}.
 */
public interface AbsenceAlertParticipant {

    void onAbsenceAlert(UUID orgId, UUID branchId, UUID personId, LocalDate lastAttended, int weeks, UUID alertId);
}
