package pe.dcs.app.features.volunteer.dto;

import pe.dcs.app.util.pagination.PaginationRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** M11 (parte 2) · Turnos y programación de voluntarios: planes de servicio, turnos, asignaciones, disponibilidad y reglas. */
public final class VolunteerDtos {

    private VolunteerDtos() {
    }

    // ---------------------------------------------------------------- plan de servicio
    public record PlanRequest(UUID branchId, LocalDate planDate, String contextType, UUID contextId, String title) {
    }

    public record PlanUpdate(LocalDate planDate, String title, Long version) {
    }

    /** force = true confirma publicar con vacantes pendientes [V13]. */
    public record PublishRequest(Boolean force) {
    }

    public record SlotResponse(UUID id, UUID branchMinistryId, String ministryName, UUID positionId, String positionName, int needed, LocalTime startTime, LocalTime endTime,
                               String notes, int confirmed, int proposed, long version, List<AssignmentResponse> assignments) {
    }

    public record PlanResponse(UUID id, UUID branchId, String branchName, LocalDate planDate, String contextType, UUID contextId, String title, String status,
                               Instant publishedAt, int slots, int vacancies, long version, List<SlotResponse> slotList) {
    }

    public record PlanSearch(Filters filters, PaginationRequest pagination) {
        public record Filters(UUID branchId, String status, LocalDate from, LocalDate to) {
        }
    }

    // ---------------------------------------------------------------- turno
    public record SlotRequest(UUID branchMinistryId, UUID positionId, Integer needed, LocalTime startTime, LocalTime endTime, String notes) {
    }

    // ---------------------------------------------------------------- asignación
    public record AssignRequest(UUID personId, Boolean overrideChecks, String overrideReason) {
    }

    public record AssignmentResponse(UUID id, UUID slotId, UUID personId, String personName, String status, String declineReason, Instant respondedAt, String screening, boolean minor) {
    }

    public record DecideRequest(String reason) {
    }

    public record ServeRequest(String status) {
    }

    /** Candidato sugerido para cubrir un turno: elegible según [V5][V6][V9][V10][V11] y ordenado de forma determinista [T04]. */
    public record Candidate(UUID personId, String personName, int shiftsThisMonth, boolean available, boolean withinLimit, String screening) {
    }

    // ---------------------------------------------------------------- disponibilidad (registrada por la administración; autoservicio en el portal, M24)
    public record AvailabilityRequest(UUID personId, LocalDate from, LocalDate to, String recurrence, Integer dayOfWeek, String reason) {
    }

    public record AvailabilityResponse(UUID id, UUID personId, String personName, LocalDate from, LocalDate to, String recurrence, Integer dayOfWeek, String reason, Instant createdAt) {
    }

    public record AvailabilitySearch(UUID personId, PaginationRequest pagination) {
    }

    // ---------------------------------------------------------------- reglas
    public record RulesRequest(Integer maxShiftsPerMonth, Integer reminderHours1, Integer reminderHours2, Integer declineLockHours, Long version) {
    }

    public record RulesResponse(int maxShiftsPerMonth, Integer reminderHours1, Integer reminderHours2, int declineLockHours, Long version) {
    }
}
