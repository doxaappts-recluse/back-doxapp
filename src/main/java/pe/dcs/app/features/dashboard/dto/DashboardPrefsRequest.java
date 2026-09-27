package pe.dcs.app.features.dashboard.dto;

import java.util.List;

/** Reemplaza la personalización completa: el orden es el de la lista (position se recalcula). */
public record DashboardPrefsRequest(List<DashboardPref> prefs) {
}
