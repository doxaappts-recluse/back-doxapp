package pe.dcs.app.features.approval.dto;

import pe.dcs.app.util.pagination.PaginationRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** M21 · DTO de la bandeja de solicitudes (motor único de aprobaciones). */
public final class ApprovalDtos {

    private ApprovalDtos() {
    }

    public record Search(Filters filters, PaginationRequest pagination) {
        /** type/status: código exacto; olderThanDays: solo las pendientes con esa antigüedad o más; mine: las que pedí yo; decidable: las que puedo decidir. */
        public record Filters(String type, String status, UUID branchId, Integer olderThanDays, Boolean mine, Boolean decidable) {
        }
    }

    public record Summary(UUID id, String type, String status, UUID branchId, String branchName, UUID relatedBranchId, String relatedBranchName,
                          UUID subjectId, String subjectName, UUID requestedById, String requestedByName, String reason, boolean waitingInfo,
                          Instant createdAt, long ageDays, Instant decidedAt, String decidedByName, String decisionNote, Instant expiresAt,
                          boolean canDecide, boolean mine, Map<String, Object> payload) {
    }

    public record Comment(UUID id, UUID authorId, String authorName, boolean byRequester, String kind, String body, Instant createdAt) {
    }

    public record Detail(Summary request, List<Comment> comments) {
    }

    public record DecisionRequest(String note) {
    }

    public record RejectRequest(String reason) {
    }

    public record CommentRequest(String body, Boolean requestInfo) {
    }

    /** action: APPROVE | REJECT (REJECT exige reason). */
    public record BulkRequest(List<UUID> ids, String action, String reason) {
    }

    public record BulkItem(UUID id, boolean ok, String message) {
    }

    public record BulkResult(int ok, int failed, List<BulkItem> items) {
    }

    public record TypeInfo(String type, String moduleCode, boolean canDecide) {
    }
}
