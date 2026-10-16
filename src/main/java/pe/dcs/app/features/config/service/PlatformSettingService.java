package pe.dcs.app.features.config.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.config.domain.PlatformSetting;
import pe.dcs.app.features.config.domain.PlatformSettingRepository;
import pe.dcs.app.features.config.dto.SettingDtos;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;

import java.util.*;

/**
 * M23 · Ajustes de plataforma (N1): el valor por defecto de cada ajuste para todas las organizaciones que no lo
 * personalizan. Validados contra el mismo esquema, con confirmación reforzada (motivo) y auditados con versión [V3].
 */
@Service
@RequiredArgsConstructor
public class PlatformSettingService {

    private static final String MODULE = "PLATFORM_SETTINGS";

    private final SettingsSchemaRegistry registry;
    private final PlatformSettingRepository repository;
    private final SettingsService settings;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<SettingDtos.PlatformItem> list() {
        Map<String, PlatformSetting> rows = new HashMap<>();
        repository.findAll().forEach(r -> rows.put(r.getKey(), r));
        List<SettingDtos.PlatformItem> out = new ArrayList<>();
        for (SettingsSchema s : registry.all()) {
            for (SettingKeyDef d : s.keys()) {
                PlatformSetting r = rows.get(s.namespace() + "." + d.key());
                out.add(new SettingDtos.PlatformItem(s.namespace() + "." + d.key(), s.namespace(), d.type(), d.labelEs(), d.labelEn(),
                        d.helpEs(), d.helpEn(), r == null ? d.defaultValue() : r.raw(), d.defaultValue(), r != null,
                        d.min(), d.max(), d.values(), r == null ? null : r.getVersion()));
            }
        }
        return out;
    }

    @Transactional
    public SettingDtos.PlatformItem save(String namespace, String key, SettingDtos.PlatformSaveRequest req) {
        SettingKeyDef def = def(namespace, key);
        confirm(req.confirm(), req.reason());
        Object value = SettingsService.coerce(def, req.value());                                    // [V3] tipo y rango
        String full = namespace.toUpperCase() + "." + key;
        PlatformSetting row = repository.findById(full).orElse(null);
        Object before = row == null ? def.defaultValue() : row.raw();
        if (row == null) {
            row = new PlatformSetting();
            row.setKey(full);
        }
        row.setDescription(descr(def));
        row.set(value);
        repository.saveAndFlush(row);
        settings.invalidateAll();
        Map<String, Object> ch = new LinkedHashMap<>();
        ch.put("from", before);
        ch.put("to", value);
        audit.record(new AuditService.Command(MODULE, "UPDATE", "PlatformSetting", full, null, null,
                Map.of(full, ch, "reason", req.reason().trim(), "version", row.getVersion())));
        return list().stream().filter(i -> i.key().equals(full)).findFirst().orElseThrow();
    }

    @Transactional
    public SettingDtos.PlatformItem reset(String namespace, String key, String reason, Boolean confirm) {
        SettingKeyDef def = def(namespace, key);
        confirm(confirm, reason);
        String full = namespace.toUpperCase() + "." + key;
        repository.findById(full).ifPresent(r -> {
            Map<String, Object> ch = new LinkedHashMap<>();
            ch.put("from", r.raw());
            ch.put("to", def.defaultValue());
            repository.delete(r);
            repository.flush();
            settings.invalidateAll();
            audit.record(new AuditService.Command(MODULE, "RESET", "PlatformSetting", full, null, null, Map.of(full, ch, "reason", reason.trim())));
        });
        return list().stream().filter(i -> i.key().equals(full)).findFirst().orElseThrow();
    }

    private SettingKeyDef def(String namespace, String key) {
        return registry.find(namespace).flatMap(s -> s.key(key))
                .orElseThrow(() -> new Exceptions("error.setting.unknownKey", HttpStatus.UNPROCESSABLE_ENTITY, namespace + "." + key));
    }

    private static void confirm(Boolean confirm, String reason) {
        if (!Boolean.TRUE.equals(confirm) || reason == null || reason.trim().length() < 5) {
            throw new Exceptions("error.setting.confirmRequired", HttpStatus.BAD_REQUEST);
        }
    }

    private static String descr(SettingKeyDef d) {
        String s = d.labelEs();
        return s.length() > 255 ? s.substring(0, 255) : s;
    }
}
