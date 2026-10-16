package pe.dcs.app.features.dashboard.dto;

/**
 * Un indicador ya calculado. kind indica cómo se dibuja: DISTRIBUTION, SERIES, EXPIRING, TABLE, SUPPORT o STATS.
 * status = OK, o UNAVAILABLE si el proveedor falló (el panel sigue mostrando los demás). route = pantalla del módulo dueño.
 */
public record WidgetResult(String code, String moduleCode, String titleKey, String size, String kind, String route,
                           int position, boolean hidden, String status, Object data) {

    public WidgetResult with(int position, boolean hidden) {
        return new WidgetResult(code, moduleCode, titleKey, size, kind, route, position, hidden, status, data);
    }
}
