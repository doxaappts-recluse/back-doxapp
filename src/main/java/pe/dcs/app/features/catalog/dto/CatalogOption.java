package pe.dcs.app.features.catalog.dto;

import java.util.UUID;

/** Opción para un select: el front elige nameEs/nameEn según el idioma activo (cae a es si falta). */
public record CatalogOption(UUID id, String code, String nameEs, String nameEn, UUID parentId) {
}
