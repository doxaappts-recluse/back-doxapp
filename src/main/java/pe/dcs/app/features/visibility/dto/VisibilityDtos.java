package pe.dcs.app.features.visibility.dto;

import pe.dcs.app.shared.vo.DocumentType;
import pe.dcs.app.util.pagination.PaginationRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** M21 · DTO de reglas de visibilidad y autorizaciones entre sedes. */
public final class VisibilityDtos {

    private VisibilityDtos() {
    }

    public record Rule(String moduleCode, String scope, boolean enabled, boolean configured, List<String> supportedScopes, Instant updatedAt) {
    }

    public record RuleRequest(String scope, Boolean enabled) {
    }

    /** La sede solicitante pide ver datos de una persona (indicada por documento) en un módulo con regla APPROVAL_REQUIRED. */
    public record AccessRequest(UUID targetBranchId, DocumentType docType, String docNumber, String moduleCode, LocalDate visibleUntil, String reason) {
    }

    public record Search(Filters filters, PaginationRequest pagination) {
        /** status: ACTIVE | EXPIRED | REVOKED (vacío = todas). */
        public record Filters(String status, UUID branchId, String moduleCode) {
        }
    }

    public record Grant(UUID id, UUID personId, String personName, UUID sourceBranchId, String sourceBranchName, UUID targetBranchId, String targetBranchName,
                        String moduleCode, LocalDate visibleUntil, String status, String approvedByName, Instant approvedAt, String revokedByName,
                        Instant revokedAt, String revokeReason, boolean canRevoke) {
    }

    public record RevokeRequest(String reason) {
    }
}
