package pe.dcs.app.features.catalog.service;

import java.util.UUID;

/**
 * Puerto de "ítem en uso" [V6]: cada módulo que guarda el código de un catálogo lo implementa
 * (p. ej. Personas usa DOCUMENT_TYPE). Sin implementaciones, solo cuentan los hijos jerárquicos.
 */
public interface CatalogUsageChecker {
    boolean inUse(UUID organizationId, String type, String code);
}
