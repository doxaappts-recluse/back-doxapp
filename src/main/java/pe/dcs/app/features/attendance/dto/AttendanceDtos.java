package pe.dcs.app.features.attendance.dto;

import pe.dcs.app.util.pagination.PaginationRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** M09 · Asistencia y check-in de niños. */
public final class AttendanceDtos {

    private AttendanceDtos() {
    }

    // ---------------------------------------------------------------- reglas
    public record Rules(int absenceWeeksAlert, int childMaxAge, int qrTtlSeconds, int reopenDays, Long version) {
    }

    public record RulesRequest(Integer absenceWeeksAlert, Integer childMaxAge, Integer qrTtlSeconds, Integer reopenDays, Long version) {
    }

    // ---------------------------------------------------------------- cultos
    /** startTime = "HH:mm"; dayOfWeek 1 (lunes) a 7 (domingo); recurrence WEEKLY | NONE. */
    public record ServiceRequest(UUID branchId, String name, String serviceType, Integer dayOfWeek, String startTime, Integer durationMin,
                                 String spaceNote, String recurrence, Boolean selfCheckinEnabled, Long version) {
    }

    public record ServiceResponse(UUID id, UUID branchId, String branchName, String name, String serviceType, String serviceTypeName, Integer dayOfWeek,
                                  String startTime, int durationMin, String spaceNote, String recurrence, boolean selfCheckinEnabled, String status,
                                  int sessionCount, long version) {
    }

    public record ServiceSearch(Filters filters, PaginationRequest pagination) {
        public record Filters(UUID branchId, String status, String q) {
        }
    }

    public record StatusRequest(String status) {
    }

    // ---------------------------------------------------------------- sesiones
    public record SessionCreate(UUID serviceId, LocalDate date) {
    }

    /** status: PLANNED | OPEN | CLOSED (vacío = todas). */
    public record SessionSearch(Filters filters, PaginationRequest pagination) {
        public record Filters(UUID branchId, UUID serviceId, String status, LocalDate from, LocalDate to) {
        }
    }

    public record SessionSummary(UUID id, UUID branchId, String branchName, String contextType, UUID contextId, String title, LocalDate date,
                                 Instant startsAt, Instant endsAt, String status, int attendees, int excused, int anonymousCount, int total,
                                 boolean selfCheckin, int children) {
    }

    public record SessionResponse(UUID id, UUID branchId, String branchName, String contextType, UUID contextId, String title, LocalDate date,
                                  Instant startsAt, Instant endsAt, String status, int attendees, int excused, int anonymousCount, int total,
                                  boolean selfCheckin, Instant opensFrom, Instant openedAt, Instant closedAt, int reopenCount, String lastReopenReason,
                                  Instant reopenUntil, long version) {
    }

    public record ReopenRequest(String reason) {
    }

    public record AnonymousRequest(Integer count) {
    }

    // ---------------------------------------------------------------- registros
    /** status: PRESENT (por defecto) | LATE | EXCUSED | ABSENT; method: MANUAL (por defecto) | LIST. */
    public record RecordRequest(UUID personId, String status, String method) {
    }

    public record BatchRequest(List<RecordRequest> items) {
    }

    public record RecordItem(UUID personId, String fullName, String status, String method, Instant at) {
    }

    public record RecordResult(RecordItem item, boolean created, boolean changed) {
    }

    public record BatchFailure(UUID personId, String message) {
    }

    public record BatchResult(int created, int updated, int unchanged, int failed, List<BatchFailure> failures) {
    }

    public record RosterSearch(String q, PaginationRequest pagination) {
    }

    public record RosterItem(UUID personId, String fullName, String docMasked, String branchName, String status, String method) {
    }

    // ---------------------------------------------------------------- QR y autoservicio
    public record QrScanRequest(UUID sessionId, String token) {
    }

    public record QrToken(String token, Instant expiresAt, int ttlSeconds) {
    }

    public record SelfRequest(String token) {
    }

    // ---------------------------------------------------------------- tendencias
    public record TrendsRequest(UUID branchId, UUID serviceId, LocalDate from, LocalDate to) {
    }

    public record TrendPoint(UUID sessionId, LocalDate date, String title, UUID branchId, String branchName, int attendees, int late, int excused,
                             int anonymousCount, int total) {
    }

    public record TrendBranch(UUID branchId, String branchName, int sessions, int total, double average) {
    }

    public record Trends(List<TrendPoint> points, List<TrendBranch> byBranch, int sessions, int total, double average) {
    }

    // ---------------------------------------------------------------- check-in de niños
    public record Guardian(UUID id, String fullName, String phone) {
    }

    public record ChildCandidate(UUID childId, String fullName, int age, String branchName, List<Guardian> guardians, boolean checkedIn) {
    }

    public record ChildSearch(UUID sessionId, String q) {
    }

    public record CheckinRequest(UUID sessionId, UUID childId, UUID guardianId, String room) {
    }

    /** allergies solo se llena con la acción H. */
    public record CheckinResponse(UUID id, UUID sessionId, String sessionTitle, UUID childId, String childName, int childAge, UUID guardianId, String guardianName,
                                  String room, String tagCode, String allergies, Instant checkedInAt, Instant checkedOutAt, UUID pickedUpBy,
                                  String pickedUpByName, String pickupMethod) {
    }

    /** Con code: el código de la etiqueta. Sin code: guardianId de un tutor autorizado y verified = true (identidad verificada). */
    public record CheckoutRequest(String code, UUID guardianId, Boolean verified) {
    }
}
