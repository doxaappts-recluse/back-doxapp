package pe.dcs.app.features.catalog.dto;

import java.util.Map;
import java.util.UUID;

public record CatalogItemResponse(UUID id, String type, String code, String nameEs, String nameEn, int sortOrder,
                                  boolean active, String source, UUID parentId, String parentNameEs, String parentNameEn,
                                  boolean hidden, boolean readOnly, boolean hasChildren, Map<String, Object> meta,
                                  Long version) {
}
