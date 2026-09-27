package pe.dcs.app.features.organization.service;

import org.springframework.stereotype.Component;

import java.util.UUID;

/** Implementación por defecto hasta que exista M15: nunca hay periodos cerrados. */
@Component
class NoFiscalPeriods implements FiscalPeriodPort {

    @Override
    public boolean hasClosedPeriod(UUID organizationId) {
        return false;
    }
}
