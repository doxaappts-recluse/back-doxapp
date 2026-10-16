package pe.dcs.app.features.organization.service;

import java.util.UUID;

/**
 * Implementación por defecto que se usó mientras no existía M15 (siempre decía que no había periodos cerrados).
 * M15 ya existe: {@code FiscalPeriodService} implementa {@link FiscalPeriodPort} de verdad. Esta clase ya NO es
 * un bean de Spring (sin @Component) — se deja solo como referencia histórica; bórrala si prefieres no conservarla.
 */
class NoFiscalPeriods implements FiscalPeriodPort {

    @Override
    public boolean hasClosedPeriod(UUID organizationId) {
        return false;
    }
}
