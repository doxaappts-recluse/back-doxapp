package pe.dcs.app.features.dashboard.widget;

/**
 * M01 · contrato de un widget. Cada módulo registra los suyos como beans (las consultas de KPI viven en el módulo dueño;
 * el panel solo las orquesta, las cachea y aplica la personalización). level = N1, N2 o N3.
 */
public interface DashboardWidgetProvider {

    String code();

    String moduleCode();

    String level();

    String titleKey();

    /** S, M o L: ancho de la tarjeta. */
    default String size() {
        return "M";
    }

    String kind();

    /** Pantalla del módulo dueño a la que lleva la tarjeta (ruta del front) o null. */
    default String route() {
        return null;
    }

    /** Orden por defecto dentro del nivel. */
    int order();

    Object data(WidgetContext ctx);
}
