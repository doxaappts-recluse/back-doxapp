package pe.dcs.app.features.config.dto;

import pe.dcs.app.features.config.service.SettingType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SettingDtos {
    private SettingDtos() {
    }

    /** source: BRANCH (override de la sede) · ORG · PLATFORM (default de plataforma) · DEFAULT (esquema). */
    public record Item(String key, SettingType type, String labelEs, String labelEn, String helpEs, String helpEn,
                       Object value, String source, boolean overridable, Double min, Double max, List<String> values,
                       Object defaultValue, Object platformDefault) {
    }

    public record View(String namespace, String moduleCode, String titleEs, String titleEn, UUID branchId, boolean canEdit, List<Item> items) {
    }

    public record NamespaceSummary(String namespace, String moduleCode, String titleEs, String titleEn, int keys, int overridable) {
    }

    public record SaveRequest(Map<String, Object> values) {
    }

    // ---- plataforma
    public record PlatformItem(String key, String namespace, SettingType type, String labelEs, String labelEn, String helpEs, String helpEn,
                               Object value, Object defaultValue, boolean custom, Double min, Double max, List<String> values,
                               Long version) {
    }

    public record PlatformSaveRequest(Object value, String reason, Boolean confirm) {
    }
}
