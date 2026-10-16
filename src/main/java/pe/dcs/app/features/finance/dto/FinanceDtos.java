package pe.dcs.app.features.finance.dto;

import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** M15 · Finanzas y donaciones (/admin/finance/*). Los montos viajan como String (BigDecimal.toPlainString()), igual que en M14. */
public final class FinanceDtos {

    private FinanceDtos() {
    }

    // ---------------------------------------------------------------- fondos

    public record FundRequest(String code, String name, String type, List<String> allowedCategories, String currency, Long version) {
    }

    public record FundView(UUID id, String code, String name, String type, List<String> allowedCategories, String currency, String status,
                           Instant createdAt, Long version) {
    }

    public record FundSearch(FundFilters filters, PaginationRequest pagination, List<SortRequest> sorts) {
        public record FundFilters(String status, String type) {
        }
    }

    // ---------------------------------------------------------------- cuentas

    public record AccountRequest(String name, String type, UUID branchId, String currency, String openingBalance, Long version) {
    }

    public record AccountView(UUID id, String name, String type, UUID branchId, String branchName, String currency, String openingBalance,
                              String status, Instant createdAt, Long version) {
    }

    public record AccountSearch(AccountFilters filters, PaginationRequest pagination, List<SortRequest> sorts) {
        public record AccountFilters(UUID branchId, String status, String type) {
        }
    }

    // ---------------------------------------------------------------- movimientos

    public record MovementRequest(UUID branchId, LocalDate movementDate, String type, String category, UUID fundId, UUID accountId, String amount,
                                  String method, UUID donorId, Boolean anonymous, String description, UUID eventId) {
    }

    public record AttachmentView(UUID id, String filename, Instant uploadedAt) {
    }

    public record MovementSummary(UUID id, UUID branchId, String branchName, LocalDate movementDate, String type, String category, UUID fundId,
                                  String fundName, UUID accountId, String accountName, String amount, String currency, String method, UUID donorId,
                                  String donorName, boolean anonymous, String description, String receiptNo, String status, UUID submittedBy,
                                  String submittedByName, Instant createdAt, Long version) {
    }

    public record MovementDetail(MovementSummary summary, String voidReason, UUID reversalOf, String sourceRef, UUID eventId, UUID cashRegisterId,
                                 UUID offeringCountId, List<AttachmentView> attachments) {
    }

    public record MovementSearch(MovementFilters filters, PaginationRequest pagination, List<SortRequest> sorts) {
        public record MovementFilters(UUID branchId, UUID fundId, UUID accountId, String status, String type, String category, LocalDate from,
                                      LocalDate to, UUID donorId) {
        }
    }

    public record DecisionRequest(String reason) {
    }

    // ---------------------------------------------------------------- conteo de ofrenda

    public record FundAmount(UUID fundId, String fundName, String amount) {
    }

    public record OfferingCountRequest(UUID branchId, UUID sessionId, UUID countedBy1, UUID countedBy2, String cashBreakdownJson, String checksAmount,
                                       List<FundAmount> funds) {
    }

    public record OfferingCountView(UUID id, UUID branchId, String branchName, UUID sessionId, UUID countedBy1, String countedBy1Name, UUID countedBy2,
                                    String countedBy2Name, String cashBreakdownJson, String checksAmount, String total, String status,
                                    Instant confirmed1At, Instant confirmed2At, List<FundAmount> funds, Instant createdAt, Long version) {
    }

    public record OfferingCountSearch(OfferingCountFilters filters, PaginationRequest pagination, List<SortRequest> sorts) {
        public record OfferingCountFilters(UUID branchId, String status) {
        }
    }

    // ---------------------------------------------------------------- caja

    public record CashRegisterOpenRequest(UUID branchId, LocalDate registerDate, String openingAmount) {
    }

    public record CashRegisterCloseRequest(String countedAmount, String notes) {
    }

    public record CashRegisterView(UUID id, UUID branchId, String branchName, LocalDate registerDate, UUID openedBy, String openedByName,
                                   String openingAmount, String expectedAmount, String countedAmount, String difference, String notes, String status,
                                   UUID closedBy, Instant closedAt, Instant createdAt, Long version) {
    }

    public record CashRegisterSearch(CashRegisterFilters filters, PaginationRequest pagination, List<SortRequest> sorts) {
        public record CashRegisterFilters(UUID branchId, String status, LocalDate from, LocalDate to) {
        }
    }

    // ---------------------------------------------------------------- presupuestos

    public record BudgetRequest(String scope, UUID branchId, UUID fundId, String category, String period, String amount, Long version) {
    }

    public record BudgetView(UUID id, String scope, UUID branchId, String branchName, UUID fundId, String fundName, String category, String period,
                             String amount, String status, String spent, String executedPct, Instant createdAt, Long version) {
    }

    public record BudgetSearch(BudgetFilters filters, PaginationRequest pagination, List<SortRequest> sorts) {
        public record BudgetFilters(UUID branchId, UUID fundId, String category, String status, String period) {
        }
    }

    // ---------------------------------------------------------------- donantes y promesas

    public record DonorRequest(UUID personId, String externalName, String externalDocId, String email, Long version) {
    }

    public record DonorView(UUID id, UUID personId, String personName, String externalName, String externalDocId, String email, Instant createdAt,
                            Long version) {
    }

    public record DonorSearch(DonorFilters filters, PaginationRequest pagination, List<SortRequest> sorts) {
        public record DonorFilters(String q) {
        }
    }

    public record PledgeRequest(UUID donorId, UUID fundId, String amount, String frequency, LocalDate startDate, LocalDate endDate, Long version) {
    }

    public record PledgeView(UUID id, UUID donorId, String donorName, UUID fundId, String fundName, String amount, String frequency,
                             LocalDate startDate, LocalDate endDate, String status, Instant createdAt, Long version) {
    }

    public record PledgeSearch(PledgeFilters filters, PaginationRequest pagination, List<SortRequest> sorts) {
        public record PledgeFilters(UUID donorId, UUID fundId, String status) {
        }
    }

    // ---------------------------------------------------------------- periodos fiscales

    public record FiscalPeriodView(UUID id, int year, int month, String status, Instant closedAt, UUID closedBy, Instant reopenedAt, UUID reopenedBy,
                                   String reopenReason, int pendingMovements) {
    }

    public record ReopenRequest(String reason) {
    }

    // ---------------------------------------------------------------- reglas de la organización

    public record FinanceRulesView(String approvalThresholdBranch, String attachmentThreshold, boolean overspendBlock, boolean selfApproval,
                                   Instant updatedAt) {
    }

    public record FinanceRulesRequest(String approvalThresholdBranch, String attachmentThreshold, Boolean overspendBlock, Boolean selfApproval) {
    }

    // ---------------------------------------------------------------- consolidado

    public record ConsolidatedRequest(LocalDate from, LocalDate to, UUID branchId) {
    }

    public record ConsolidatedRow(UUID branchId, String branchName, UUID fundId, String fundName, String category, String type, String total) {
    }

    public record ConsolidatedResponse(List<ConsolidatedRow> rows, String totalIncome, String totalExpense, String balance) {
    }
}
