package pe.dcs.app.features.config.service;

import java.util.List;

/**
 * Definición de un ajuste dentro del esquema de un módulo. min/max: valor (INT/DECIMAL) o longitud (STRING).
 * {@code branchOverridable}: una sede puede tener su propio valor [V13].
 */
public record SettingKeyDef(String key, SettingType type, Object defaultValue, Double min, Double max, List<String> values,
                            boolean branchOverridable, String labelEs, String labelEn, String helpEs, String helpEn) {

    public static SettingKeyDef integer(String key, int def, int min, int max, boolean branchOverridable,
                                        String labelEs, String labelEn, String helpEs, String helpEn) {
        return new SettingKeyDef(key, SettingType.INT, def, (double) min, (double) max, null, branchOverridable, labelEs, labelEn, helpEs, helpEn);
    }
}
