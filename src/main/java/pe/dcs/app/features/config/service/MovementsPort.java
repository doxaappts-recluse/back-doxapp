package pe.dcs.app.features.config.service;

import java.util.UUID;

/** ¿Tiene la organización movimientos (finanzas/planilla) que dependan de la moneda o la zona? Lo implementa M15. */
public interface MovementsPort {
    boolean hasMovements(UUID organizationId);
}
