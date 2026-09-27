package pe.dcs.app.features.catalog.service;

/**
 * Tipo de catálogo (definido en código por el módulo dueño, M23 §Entidades).
 *
 * @param editableByOrg  la organización puede ocultar los BASE y desactivar sus propios ítems
 * @param extensible     la organización puede agregar ítems propios
 * @param parentType     tipo del padre (jerárquicos) o nulo
 * @param publicRead     se sirve sin sesión (/public/catalogs)
 * @param mandatory      [V12] siempre debe quedar al menos un ítem activo visible
 */
public record CatalogTypeDef(String code, String ownerModule, boolean editableByOrg, boolean extensible,
                             String parentType, boolean publicRead, boolean mandatory) {
    public boolean hierarchical() {
        return parentType != null;
    }
}
