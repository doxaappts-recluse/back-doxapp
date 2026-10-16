package pe.dcs.app.features.transfer.dto;

import pe.dcs.app.shared.vo.DocumentType;
import pe.dcs.app.util.pagination.PaginationRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** M21 · DTO de traslados de sede. */
public final class TransferDtos {

    private TransferDtos() {
    }

    /** Qué más se cierra al ejecutar. Sin valor se asume true (los módulos M08, M10 y M11 lo consumen cuando existan). */
    public record Options(Boolean moveMembership, Boolean endGroups, Boolean endMinistries) {
    }

    /** La persona se indica por id o por documento (quien pide desde la sede destino puede no verla en su lista). */
    public record CreateRequest(UUID personId, DocumentType docType, String docNumber, UUID toBranchId, String reason, LocalDate effectiveDate, Options options) {
    }

    public record Search(Filters filters, PaginationRequest pagination) {
        public record Filters(String status, UUID branchId, String q) {
        }
    }

    public record Summary(UUID id, UUID requestId, String status, UUID personId, String personName, UUID fromBranchId, String fromBranchName,
                          UUID toBranchId, String toBranchName, LocalDate effectiveDate, String reason, Options options, boolean forced,
                          UUID requestedById, String requestedByName, Instant createdAt, Instant executedAt, boolean canExecute, boolean canCancel) {
    }

    /** Sede activa a la que se puede trasladar (toda la organización, no solo el alcance de quien pide). */
    public record Destination(UUID id, String name, String code) {
    }

    public record Impact(String key, int count) {
    }

    public record Preview(Summary transfer, List<Impact> impacts, boolean dueToday) {
    }
}
