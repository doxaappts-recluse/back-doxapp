package pe.dcs.app.features.organization.service;

import java.util.UUID;

/**
 * M02 [V13] · Puerto hacia M15 (Finanzas): ¿la organización ya tiene un periodo fiscal cerrado? Mientras exista uno no
 * se puede cambiar el mes de inicio del año fiscal. Implementado por {@code FiscalPeriodService} (M15).
 */
public interface FiscalPeriodPort {

    boolean hasClosedPeriod(UUID organizationId);
}
