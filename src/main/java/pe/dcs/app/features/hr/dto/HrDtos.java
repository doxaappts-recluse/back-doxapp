package pe.dcs.app.features.hr.dto;

import pe.dcs.app.util.pagination.PaginationRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** M17 · DTOs de RRHH y planilla: personal (HR_STAFF), vacaciones y permisos (HR_LEAVE), planilla (HR_PAYROLL). */
public final class HrDtos {

    private HrDtos() {
    }

    // ---------------------------------------------------------------- personal
    public record StaffRequest(UUID personId, String position, UUID ministryId, String contractType, LocalDate hireDate, LocalDate contractEnd,
                               String baseSalary, String currency, String payFrequency, String bank, UUID branchId, Long version) {
    }

    public record StaffView(UUID id, UUID branchId, String branchName, UUID personId, String personName, String position, UUID ministryId,
                            String ministryName, String contractType, LocalDate hireDate, LocalDate contractEnd, LocalDate terminationDate,
                            String terminationReason, String baseSalary, String currency, String payFrequency, String bank, boolean sensitiveVisible,
                            String status, Instant createdAt, long version) {
    }

    public record StaffSearch(StaffFilters filters, PaginationRequest pagination) {
        public record StaffFilters(UUID branchId, String status, String contractType, String q) {
        }
    }

    public record TerminateRequest(LocalDate terminationDate, String reason) {
    }

    public record SalaryChangeRequest(String amount, LocalDate fromDate, String reason) {
    }

    public record SalaryHistoryView(UUID id, String amount, LocalDate fromDate, String reason, Instant createdAt) {
    }

    // ---------------------------------------------------------------- vacaciones y permisos
    public record LeaveSubmitRequest(UUID staffId, String type, LocalDate startDate, LocalDate endDate, String reason, String attachmentKey) {
    }

    public record LeaveView(UUID id, UUID staffId, String staffName, UUID branchId, String type, LocalDate startDate, LocalDate endDate, String days,
                            String reason, String attachmentKey, String status, UUID decidedBy, String decidedByName, String decisionReason,
                            UUID approvalRequestId, Instant createdAt, long version) {
    }

    public record LeaveSearch(LeaveFilters filters, PaginationRequest pagination) {
        public record LeaveFilters(UUID staffId, UUID branchId, String status, String type, LocalDate from, LocalDate to) {
        }
    }

    public record LeaveDecisionRequest(String reason) {
    }

    public record LeaveBalanceView(UUID staffId, String staffName, int year, String entitled, String taken, String pending, String available) {
    }

    public record LeaveBalanceAdjustRequest(String entitled) {
    }

    // ---------------------------------------------------------------- conceptos de planilla
    public record ConceptRequest(String code, String nameEs, String nameEn, String kind, String calc, String value, Boolean mandatory, Boolean active,
                                 Integer sortOrder, Long version) {
    }

    public record ConceptView(UUID id, String code, String nameEs, String nameEn, String kind, String calc, String value, boolean mandatory,
                              boolean active, int sortOrder, long version) {
    }

    // ---------------------------------------------------------------- corridas de planilla
    public record RunCreateRequest(UUID branchId, String period) {
    }

    public record RunView(UUID id, UUID branchId, String branchName, String period, String status, Instant calculatedAt, Instant approvedAt,
                          Instant paidAt, Instant closedAt, UUID financialMovementId, String totalGross, String totalDeductions, String totalNet,
                          String totalEmployerCost, int staffCount, Instant createdAt, long version) {
    }

    public record RunSearch(RunFilters filters, PaginationRequest pagination) {
        public record RunFilters(UUID branchId, String status, String period) {
        }
    }

    public record RunPayRequest(UUID fundId, UUID branchId) {
    }

    public record RecordLineView(String conceptCode, String conceptName, String kind, String amount) {
    }

    public record RecordView(UUID id, UUID staffId, String staffName, String position, String gross, String deductions, String net,
                             String employerCost, String workedDays, String unpaidDays, String payslipNo, List<RecordLineView> lines,
                             boolean sensitiveVisible) {
    }
}
