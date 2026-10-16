package pe.dcs.app.features.ministry.dto;

import pe.dcs.app.util.pagination.PaginationRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** M11 · Ministerios: estructura, cargos, ministerios por sede, asignaciones, verificaciones (screening) y solicitudes de ingreso. */
public final class MinistryDtos {

    private MinistryDtos() {
    }

    // ---------------------------------------------------------------- estructura
    public record PositionRequest(String name, Boolean leader, Integer sortOrder) {
    }

    public record PositionResponse(UUID id, String name, boolean leader, int sortOrder, int activeAssignments) {
    }

    /** positions es opcional al crear (los cargos también se agregan después). */
    public record MinistryRequest(String name, String description, String color, UUID parentId, Boolean requiresScreening, String screeningType, Boolean adultOnly,
                                  List<PositionRequest> positions) {
    }

    public record MinistryUpdate(String name, String description, String color, UUID parentId, Boolean requiresScreening, String screeningType, Boolean adultOnly, Long version) {
    }

    public record MinistryResponse(UUID id, String name, String description, String color, UUID parentId, String parentName, boolean requiresScreening, String screeningType,
                                   boolean adultOnly, String status, List<PositionResponse> positions, int branches, int activeAssignments, Instant createdAt, long version) {
    }

    public record MinistrySearch(Filters filters, PaginationRequest pagination) {
        public record Filters(String q, String status, UUID parentId) {
        }
    }

    public record ReasonRequest(String reason) {
    }

    // ---------------------------------------------------------------- ministerio en la sede
    public record BranchMinistryRequest(UUID ministryId, UUID branchId, UUID leaderPersonId) {
    }

    public record LeaderRequest(UUID leaderPersonId) {
    }

    public record BranchMinistryResponse(UUID id, UUID ministryId, String ministryName, String color, boolean requiresScreening, boolean adultOnly, UUID branchId, String branchName,
                                         UUID leaderId, String leaderName, String status, int team, int pendingRequests, long version) {
    }

    public record BranchMinistrySearch(Filters filters, PaginationRequest pagination) {
        public record Filters(String q, UUID branchId, UUID ministryId, String status) {
        }
    }

    // ---------------------------------------------------------------- asignaciones
    public record AssignRequest(UUID branchMinistryId, UUID personId, UUID positionId, LocalDate from) {
    }

    public record EndRequest(LocalDate to, String reason) {
    }

    /** screening: OK | NOT_REQUIRED | MISSING | PENDING | EXPIRED (estado de la verificación que exige el ministerio, hoy). */
    public record AssignmentResponse(UUID id, UUID branchMinistryId, UUID ministryId, String ministryName, String color, UUID branchId, String branchName, UUID personId, String personName,
                                     UUID positionId, String positionName, boolean leader, LocalDate from, LocalDate to, String status, String endReason, String screening, boolean minor) {
    }

    public record AssignmentSearch(Filters filters, PaginationRequest pagination) {
        public record Filters(UUID branchMinistryId, UUID ministryId, UUID branchId, UUID personId, String status, String q) {
        }
    }

    // ---------------------------------------------------------------- verificaciones
    public record ScreeningRequest(UUID personId, String type, String status, LocalDate issuedAt, LocalDate expiresAt, String docRef, String notes, Long version) {
    }

    /** status es el efectivo hoy (CLEARED vencido = EXPIRED); storedStatus es el guardado. */
    public record ScreeningResponse(UUID id, UUID personId, String personName, String type, String status, String storedStatus, LocalDate issuedAt, LocalDate expiresAt, String docRef,
                                    String notes, int activeRiskAssignments, long version) {
    }

    public record ScreeningSearch(Filters filters, PaginationRequest pagination) {
        public record Filters(String q, String status, String type, Integer expiringDays) {
        }
    }

    // ---------------------------------------------------------------- solicitudes de ingreso
    public record JoinRequest(UUID personId, UUID positionId, String message) {
    }

    public record JoinRequestRow(UUID id, UUID personId, String personName, String positionName, String requestedByName, String message, String screening, Instant createdAt) {
    }

    /** Ministerios de una persona (para su ficha). */
    public record PersonMinistry(UUID assignmentId, UUID branchMinistryId, String ministryName, String branchName, String positionName, boolean leader, LocalDate from, LocalDate to, String status) {
    }
}
