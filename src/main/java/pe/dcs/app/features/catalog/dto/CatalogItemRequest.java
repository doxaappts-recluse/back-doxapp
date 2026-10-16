package pe.dcs.app.features.catalog.dto;

import java.util.Map;
import java.util.UUID;

/** {@code code} solo se usa al crear (inmutable después). */
public record CatalogItemRequest(String code, String nameEs, String nameEn, Integer sortOrder, UUID parentId,
                                 Map<String, Object> meta, Long version) {
}
