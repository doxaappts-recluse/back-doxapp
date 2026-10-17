package features.contract.dto;

import features.contract.domain.ContractScope;
import features.contract.domain.LicenseDistributionMode;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Términos comerciales de un contrato. Se usa para crear, editar un PENDING, corregir y para renovar/mejorar/reducir
 * (en esas transiciones {@code startDate} y {@code organizationId} se ignoran: los calcula el servidor).
 * {@code modules} vacío = los módulos del plan. {@code version} opcional: control de concurrencia.
 */
public record ContractRequest(
        UUID organizationId,
        UUID planId,
        BigDecimal price,
        @Size(max = 3) String currency,
        LocalDate startDate,
        LocalDate endDate,
        Integer maxLicenses,
        Integer maxBranches,
        LicenseDistributionMode distributionMode,
        ContractScope scope,
        UUID branchId,
        List<String> modules,
        List<Allocation> allocations,
        Boolean sameTypeTransition,
        Long version
) {
    public record Allocation(UUID branchId, Integer allocatedLicenses) {
    }
}
