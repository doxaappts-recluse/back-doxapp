package pe.dcs.app.features.facility.dto;

import pe.dcs.app.util.pagination.PaginationRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** M16 · DTOs de Espacios (reservas) e Inventario. */
public final class FacilityDtos {

    private FacilityDtos() {
    }

    // ---------------------------------------------------------------- espacios
    public record SpaceRequest(String name, String typeCode, Integer capacity, List<String> equipment, Boolean requiresApproval,
                               LocalTime openFrom, LocalTime openTo, Integer bufferMinutes, UUID branchId, Long version) {
    }

    public record SpaceView(UUID id, UUID branchId, String branchName, String name, String typeCode, Integer capacity, List<String> equipment,
                            boolean requiresApproval, LocalTime openFrom, LocalTime openTo, int bufferMinutes, String status, Instant createdAt, long version) {
    }

    public record SpaceSearch(SpaceFilters filters, PaginationRequest pagination) {
        public record SpaceFilters(UUID branchId, String status, String typeCode, String q) {
        }
    }

    public record SpaceRulesView(int maxDurationHours, String reservationRequesters, int maxRecurrence, Instant updatedAt) {
    }

    public record SpaceRulesRequest(Integer maxDurationHours, String reservationRequesters, Integer maxRecurrence) {
    }

    // ---------------------------------------------------------------- reservas
    public record RecurrenceRequest(String frequency, Integer occurrences) {
    }

    public record ReservationRequest(UUID spaceId, String title, String sourceType, UUID sourceId, Instant startAt, Instant endAt,
                                     Integer attendeesEst, RecurrenceRequest recurrence, Boolean confirm) {
    }

    public record ReservationView(UUID id, UUID spaceId, String spaceName, UUID branchId, String branchName, UUID requestedBy, String requestedByName,
                                  String title, String sourceType, UUID sourceId, Instant startAt, Instant endAt, Integer attendeesEst, String status,
                                  String decisionReason, UUID recurrenceGroupId, boolean overCapacity, Instant createdAt, long version) {
    }

    public record OccurrenceConflict(Instant startAt, Instant endAt) {
    }

    public record ReservationSubmitResult(boolean preview, List<ReservationView> created, List<OccurrenceConflict> conflicts, int totalOccurrences) {
    }

    public record ReservationSearch(ReservationFilters filters, PaginationRequest pagination) {
        public record ReservationFilters(UUID spaceId, UUID branchId, String status, Instant from, Instant to, Boolean mine) {
        }
    }

    public record ReservationDecisionRequest(String reason) {
    }

    public record AvailabilityRequest(UUID spaceId, LocalDate date) {
    }

    public record BusyWindow(Instant startAt, Instant endAt) {
    }

    public record AvailabilityView(UUID spaceId, LocalDate date, LocalTime openFrom, LocalTime openTo, List<BusyWindow> busy) {
    }

    // ---------------------------------------------------------------- inventario
    public record ItemRequest(String code, String name, String categoryCode, String kind, String unit, String initialQuantity, String minStock,
                              String location, LocalDate acquisitionDate, String cost, String currency, String condition, String serialNo,
                              UUID branchId, Long version) {
    }

    public record ItemView(UUID id, UUID branchId, String branchName, String code, String name, String categoryCode, String kind, String unit,
                           String quantity, String minStock, String location, LocalDate acquisitionDate, String cost, String currency, String condition,
                           String serialNo, boolean hasPhoto, String status, Instant createdAt, long version) {
    }

    public record ItemSearch(ItemFilters filters, PaginationRequest pagination) {
        public record ItemFilters(UUID branchId, String kind, String categoryCode, String status, String q, Boolean lowStock) {
        }
    }

    public record MovementRequest(UUID itemId, String type, String reason, String quantity, LocalDate movementDate, String unitCost, UUID fundId, String note) {
    }

    public record MovementView(UUID id, UUID itemId, String itemName, String type, String reason, String quantity, LocalDate movementDate, String unitCost,
                               UUID financialMovementId, UUID transferGroupId, String note, Instant createdAt) {
    }

    public record TransferRequest(UUID itemId, UUID toBranchId, String quantity, String note) {
    }

    public record AssignmentRequest(UUID itemId, String assigneeType, UUID assigneePersonId, UUID assigneeMinistryId, UUID assigneeSpaceId,
                                    LocalDate assignedDate, LocalDate expectedReturn) {
    }

    public record AssignmentReturnRequest(LocalDate returnedDate, String returnCondition) {
    }

    public record AssignmentView(UUID id, UUID itemId, String itemName, String assigneeType, UUID assigneeId, String assigneeLabel, LocalDate assignedDate,
                                 LocalDate expectedReturn, LocalDate returnedDate, String returnCondition, String status, Instant createdAt, long version) {
    }

    public record AssignmentSearch(AssignmentFilters filters, PaginationRequest pagination) {
        public record AssignmentFilters(UUID branchId, UUID itemId, String status, UUID assigneePersonId) {
        }
    }

    // ---------------------------------------------------------------- conteo físico
    public record CountStartRequest(UUID branchId, LocalDate countDate, List<UUID> itemIds) {
    }

    public record CountLineEntry(UUID itemId, String counted, String note) {
    }

    public record CountEnterRequest(List<CountLineEntry> lines) {
    }

    public record CountLineView(UUID itemId, String itemCode, String itemName, String expected, String counted, boolean adjusted, String note) {
    }

    public record CountView(UUID id, UUID branchId, LocalDate countDate, String status, List<CountLineView> lines, Instant closedAt, long version) {
    }

    // ---------------------------------------------------------------- importación de inventario
    public record ImportRowView(int row, String action, String code, String name, String message) {
    }

    public record ImportSummary(UUID jobId, String status, String fileName, int total, int create, int update, int skip, int error,
                                List<ImportRowView> sample, boolean truncated) {
    }
}
