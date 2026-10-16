package pe.dcs.app.features.catalog.dto;

import java.util.List;
import java.util.UUID;

public record CatalogReorderRequest(List<UUID> ids) {
}
