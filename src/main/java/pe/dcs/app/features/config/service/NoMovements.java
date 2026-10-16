package pe.dcs.app.features.config.service;

import org.springframework.stereotype.Component;

import java.util.UUID;

/** Implementación por defecto hasta que exista M15: nunca hay movimientos. */
@Component
class NoMovements implements MovementsPort {

    @Override
    public boolean hasMovements(UUID organizationId) {
        return false;
    }
}
