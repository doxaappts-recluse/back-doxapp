package pe.dcs.app.features.contract.dto;

import pe.dcs.app.features.contract.domain.ContractScope;
import pe.dcs.app.features.contract.domain.ContractStatus;
import pe.dcs.app.features.contract.domain.LicenseDistributionMode;
import pe.dcs.app.features.contract.domain.RenewalType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Contrato con su contexto. {@code licenses}, {@code allocations}, {@code branchesUsed} e {@code history} solo vienen en el
 * detalle (null en las filas de la lista). {@code actions}: transiciones disponibles en el estado actual.
 */
public record ContractResponse(
        UUID id, UUID organizationId, String organizationName, String organizationSlug,
        UUID planId, String planName, BigDecimal price, String currency,
        LocalDate startDate, LocalDate endDate, int maxLicenses, Integer maxBranches,
        LicenseDistributionMode distributionMode, ContractScope scope, UUID branchId, String branchName,
        ContractStatus status, String statusReason, RenewalType renewalType, UUID previousContractId,
        Instant activatedAt, Instant suspendedAt, Instant cancelledAt, Instant replacedAt, Instant expiredAt,
        List<ModuleRef> modules, List<AllocationItem> allocations, Licenses licenses, Long branchesUsed,
        Long daysToExpire, List<String> actions, List<HistoryItem> history,
        Instant createdAt, Instant updatedAt, Long version
) {
    public record ModuleRef(String code, String nameEs, String nameEn) {
    }

    public record AllocationItem(UUID branchId, String branchName, int allocated, long used) {
    }

    public record Licenses(int max, long used, long available) {
    }

    public record HistoryItem(UUID id, ContractStatus status, RenewalType renewalType, String planName, BigDecimal price,
                              String currency, LocalDate startDate, LocalDate endDate, int maxLicenses,
                              Instant activatedAt, Instant replacedAt, boolean current) {
    }
}
