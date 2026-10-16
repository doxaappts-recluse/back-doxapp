package pe.dcs.app.features.catalog.dto;

public record CatalogTypeResponse(String code, String ownerModule, boolean editableByOrg, boolean extensible,
                                  String parentType, boolean publicRead, boolean mandatory, long total, long active) {
}
