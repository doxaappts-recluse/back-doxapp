package pe.dcs.app.features.pastoral.dto;

import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** M12 · Cuidado pastoral: casos de seguimiento (/admin/pastoral-cases) y peticiones de oración (/admin/prayer-requests). */
public final class PastoralDtos {

    private PastoralDtos() {
    }

    // ---------------------------------------------------------------- casos pastorales

    public record CaseSearch(CaseFilters filters, PaginationRequest pagination, List<SortRequest> sorts) {
        /** mine = asignados a mí · open = OPEN/IN_PROGRESS · overdue = SLA vencido según pastoral_rules. */
        public record CaseFilters(String q, UUID branchId, String type, String status, String priority, UUID assignedTo, Boolean mine,
                                  Boolean open, Boolean overdue, Boolean unassigned) {
        }
    }

    public record CaseCreateRequest(UUID personId, UUID branchId, String type, String priority, UUID assignedTo, String confidentiality,
                                    LocalDate dueAt, String initialNote) {
    }

    public record AssignRequest(UUID assignedTo, String reason) {
    }

    public record ContactRequest(String method, String result, String notes, LocalDate nextActionDate) {
    }

    public record NoteRequest(String text, Boolean restricted) {
    }

    public record ResolveRequest(String result, String notes) {
    }

    public record CaseSummary(UUID id, UUID personId, String personName, UUID branchId, String branchName, String type, String priority,
                              String status, UUID assignedTo, String assignedName, String source, String confidentiality, LocalDate dueAt,
                              Instant lastContactAt, boolean overdue, Instant createdAt) {
    }

    public record NoteView(UUID id, UUID authorId, String authorName, String text, boolean restricted, Instant createdAt) {
    }

    public record ContactView(UUID id, Instant at, String method, String result, String notes, LocalDate nextActionDate, UUID byPersonId,
                              String byName) {
    }

    public record AssignmentView(UUID id, UUID fromPersonId, String fromName, UUID toPersonId, String toName, String reason, UUID byPersonId,
                                 Instant at) {
    }

    public record CaseResponse(CaseSummary summary, String result, Instant resolvedAt, Instant closedAt, List<NoteView> notes,
                               List<ContactView> contacts, List<AssignmentView> history, Long version) {
    }

    public record RulesRequest(Integer absenceWeeks, Integer slaHoursHigh, Integer slaHoursNormal, Integer slaHoursLow, Long version) {
    }

    public record RulesResponse(int absenceWeeks, int slaHoursHigh, int slaHoursNormal, int slaHoursLow, Long version) {
    }

    // ---------------------------------------------------------------- oración

    public record PrayerSearch(PrayerFilters filters, PaginationRequest pagination, List<SortRequest> sorts) {
        public record PrayerFilters(String q, UUID branchId, String category, String status, String moderation, String visibility, Boolean mine) {
        }
    }

    public record PrayerCreateRequest(UUID forPersonId, String text, String category, String visibility, Boolean anonymous) {
    }

    public record ModerateRequest(String decision, String reason) {
    }

    public record AnswerRequest(String testimony, LocalDate answeredAt) {
    }

    public record PrayerSummary(UUID id, UUID requestedBy, String requestedByName, UUID forPersonId, String forPersonName, String text,
                                String category, String visibility, boolean anonymous, String moderation, String status, long supportCount,
                                Instant createdAt) {
    }

    public record PrayerResponse(PrayerSummary summary, String rejectReason, Instant answeredAt, String testimony, Long version) {
    }

    /** Ítem del muro público de oración: solo CONGREGATION + APPROVED; sin autor si es anónima [V12]. */
    public record WallItem(UUID id, String authorName, String text, String category, long supportCount, boolean answered, Instant createdAt) {
    }
}
