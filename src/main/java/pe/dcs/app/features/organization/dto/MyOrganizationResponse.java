package pe.dcs.app.features.organization.dto;

import pe.dcs.app.features.organization.domain.OrganizationStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * "Mi organización". ORG_BRANCH_ADMIN la ve solo en lectura y sin RUC ni razón social (canEdit=false, esos campos nulos).
 * {@code contract} es null si la organización no tiene un contrato vigente.
 */
public record MyOrganizationResponse(
        UUID id, String name, String legalName, String taxId, String slug, String country, String email, String phone,
        LocalDate foundedDate, String timezone, String currency, String defaultLanguage, short fiscalYearStartMonth,
        AddressDto address, OrganizationStatus status, boolean canEdit, ContractSummary contract
) {
    public record ContractSummary(String status, LocalDate startDate, LocalDate endDate, int maxLicenses, List<String> modules) {
    }
}
