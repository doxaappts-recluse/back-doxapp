package pe.dcs.app.features.organization.dto;

import pe.dcs.app.features.organization.domain.OrganizationStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Organización vista por la plataforma (N1). {@code activation} es la lista de requisitos para activar [V6]. */
public record OrgResponse(
        UUID id, String name, String legalName, String taxId, String slug, String country, String email, String phone,
        LocalDate foundedDate, String timezone, String currency, String defaultLanguage, short fiscalYearStartMonth,
        AddressDto address, OrganizationStatus status, String statusReason, Instant trialEndsAt, Instant activatedAt,
        Instant closedAt, LocalDate retentionUntil, Activation activation, List<OrganizationStatus> allowedTransitions,
        List<SlugRedirectInfo> redirects, Instant createdAt, Instant updatedAt
) {
    /** Requisitos para activar: contrato vigente, sede principal activa y al menos un administrador de organización. */
    public record Activation(boolean contract, boolean mainBranch, boolean orgAdmin) {
        public boolean ready() {
            return contract && mainBranch && orgAdmin;
        }
    }

    public record SlugRedirectInfo(String oldSlug, Instant until) {
    }
}
