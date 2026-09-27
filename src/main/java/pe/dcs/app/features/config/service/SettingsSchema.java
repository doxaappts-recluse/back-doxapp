package pe.dcs.app.features.config.service;

import java.util.List;
import java.util.Optional;

/** Esquema de ajustes de un módulo (namespace). {@code moduleCode}: el módulo debe estar disponible para la organización [V7]. */
public record SettingsSchema(String namespace, String moduleCode, String titleEs, String titleEn, List<SettingKeyDef> keys) {
    public Optional<SettingKeyDef> key(String key) {
        return keys.stream().filter(k -> k.key().equals(key)).findFirst();
    }
}
