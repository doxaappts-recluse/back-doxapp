package pe.dcs.app.features.config.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.branch.domain.Branch;
import pe.dcs.app.features.branch.domain.BranchRepository;
import pe.dcs.app.features.config.domain.*;
import pe.dcs.app.features.config.dto.SettingDtos;
import pe.dcs.app.features.module.domain.AppModule;
import pe.dcs.app.features.module.domain.ModuleKind;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.ContractGate;
import pe.dcs.app.security.authz.ModuleCatalog;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M23 · Ajustes por módulo. Valor efectivo = sede → organización → default de plataforma → default del esquema.
 * Validado contra el {@link SettingsSchema} del módulo [V7] [V13] [V14]. Efecto inmediato: cada escritura invalida la
 * caché de la organización (en otras instancias, como máximo 60 s).
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final String MODULE = "ORG_SETTINGS";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final SettingsSchemaRegistry registry;
    private final OrgSettingRepository settings;
    private final PlatformSettingRepository platform;
    private final BranchRepository branches;
    private final ModuleCatalog modules;
    private final ContractGate contractGate;
    private final OrgGuard guard;
    private final AuditService audit;
    private final Clock clock;

    private record OrgSnapshot(Map<String, Object> values, Instant loadedAt) {
    }

    private record PlatSnapshot(Map<String, Object> values, Instant loadedAt) {
    }

    private final ConcurrentHashMap<UUID, OrgSnapshot> orgCache = new ConcurrentHashMap<>();
    private volatile PlatSnapshot platCache;

    // ---------------------------------------------------------------- lectura efectiva (la usan los demás módulos)

    public Object effective(UUID orgId, UUID branchId, String namespace, String key) {
        SettingKeyDef def = registry.find(namespace).flatMap(s -> s.key(key))
                .orElseThrow(() -> new Exceptions("error.setting.unknownKey", HttpStatus.UNPROCESSABLE_ENTITY, namespace + "." + key));
        return resolve(orgId, branchId, namespace.toUpperCase(), def).value();
    }

    public int getInt(UUID orgId, UUID branchId, String namespace, String key) {
        return ((Number) effective(orgId, branchId, namespace, key)).intValue();
    }

    public boolean getBool(UUID orgId, UUID branchId, String namespace, String key) {
        return Boolean.TRUE.equals(effective(orgId, branchId, namespace, key));
    }

    private record Resolved(Object value, String source, Object platformDefault) {
    }

    private Resolved resolve(UUID orgId, UUID branchId, String ns, SettingKeyDef def) {
        Map<String, Object> plat = platformValues();
        Object platVal = plat.get(ns + "." + def.key());
        if (orgId != null) {
            Map<String, Object> org = orgValues(orgId);
            if (branchId != null && def.branchOverridable() && org.containsKey(k(branchId, ns, def.key()))) {
                return new Resolved(org.get(k(branchId, ns, def.key())), "BRANCH", platVal);
            }
            if (org.containsKey(k(null, ns, def.key()))) {
                return new Resolved(org.get(k(null, ns, def.key())), "ORG", platVal);
            }
        }
        if (platVal != null) {
            return new Resolved(platVal, "PLATFORM", platVal);
        }
        return new Resolved(def.defaultValue(), "DEFAULT", null);
    }

    private static String k(UUID branchId, String ns, String key) {
        return (branchId == null ? "-" : branchId) + "|" + ns + "|" + key;
    }

    private Map<String, Object> orgValues(UUID orgId) {
        OrgSnapshot s = orgCache.get(orgId);
        if (s == null || s.loadedAt().plus(TTL).isBefore(clock.instant())) {
            Map<String, Object> m = new HashMap<>();
            for (OrgSetting o : settings.findByOrganizationId(orgId)) {
                m.put(k(o.getBranchId(), o.getNamespace(), o.getKey()), o.raw());
            }
            s = new OrgSnapshot(m, clock.instant());
            orgCache.put(orgId, s);
        }
        return s.values();
    }

    private Map<String, Object> platformValues() {
        PlatSnapshot s = platCache;
        if (s == null || s.loadedAt().plus(TTL).isBefore(clock.instant())) {
            Map<String, Object> m = new HashMap<>();
            for (PlatformSetting p : platform.findAll()) {
                m.put(p.getKey(), p.raw());
            }
            s = new PlatSnapshot(m, clock.instant());
            platCache = s;
        }
        return s.values();
    }

    public void invalidateOrg(UUID orgId) {
        orgCache.remove(orgId);
    }

    public void invalidateAll() {
        orgCache.clear();
        platCache = null;
    }

    // ---------------------------------------------------------------- pantalla (N2/N3)

    @Transactional(readOnly = true)
    public List<SettingDtos.NamespaceSummary> namespaces(AuthenticatedActor actor) {
        return registry.all().stream().filter(s -> available(actor.organizationId(), s))
                .map(s -> new SettingDtos.NamespaceSummary(s.namespace(), s.moduleCode(), s.titleEs(), s.titleEn(), s.keys().size(),
                        (int) s.keys().stream().filter(SettingKeyDef::branchOverridable).count())).toList();
    }

    @Transactional(readOnly = true)
    public SettingDtos.View view(AuthenticatedActor actor, AccessScope scope, String namespace, UUID branchId) {
        SettingsSchema schema = schema(namespace);
        assertAvailable(actor.organizationId(), schema);
        if (branchId != null) {
            branch(scope, branchId);
        }
        return build(actor, schema, branchId);
    }

    @Transactional
    public SettingDtos.View save(AuthenticatedActor actor, AccessScope scope, String namespace, UUID branchId, Map<String, Object> values) {
        UUID org = actor.organizationId();
        SettingsSchema schema = schema(namespace);
        assertAvailable(org, schema);
        assertWriter(actor, branchId);
        guard.assertOpen(org);
        if (branchId != null) {
            branch(scope, branchId);
        }
        if (values == null || values.isEmpty()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "values");
        }
        Map<String, Object> clean = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            SettingKeyDef def = schema.key(e.getKey())
                    .orElseThrow(() -> new Exceptions("error.setting.unknownKey", HttpStatus.UNPROCESSABLE_ENTITY, e.getKey()));
            if (branchId != null && !def.branchOverridable()) {                                    // [V13]
                throw new Exceptions("error.setting.notOverridable", HttpStatus.FORBIDDEN);
            }
            clean.put(def.key(), coerce(def, e.getValue()));                                        // [V7] [V14]
        }
        Map<String, Object> diff = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : clean.entrySet()) {
            OrgSetting row = find(org, branchId, schema.namespace(), e.getKey());
            Object before = row == null ? null : row.raw();
            if (row == null) {
                row = new OrgSetting();
                row.setOrganizationId(org);
                row.setBranchId(branchId);
                row.setNamespace(schema.namespace());
                row.setKey(e.getKey());
            }
            if (!Objects.equals(before, e.getValue())) {
                row.set(e.getValue());
                settings.save(row);
                Map<String, Object> ch = new LinkedHashMap<>();
                ch.put("from", before);
                ch.put("to", e.getValue());
                diff.put(schema.namespace() + "." + e.getKey(), ch);
            }
        }
        settings.flush();
        invalidateOrg(org);
        if (!diff.isEmpty()) {
            audit.record(new AuditService.Command(MODULE, "UPDATE", "OrgSetting", schema.namespace(), org, branchId, diff));
        }
        return build(actor, schema, branchId);
    }

    @Transactional
    public SettingDtos.View reset(AuthenticatedActor actor, AccessScope scope, String namespace, UUID branchId, String key) {
        UUID org = actor.organizationId();
        SettingsSchema schema = schema(namespace);
        assertAvailable(org, schema);
        assertWriter(actor, branchId);
        guard.assertOpen(org);
        if (branchId != null) {
            branch(scope, branchId);
        }
        SettingKeyDef def = schema.key(key).orElseThrow(() -> new Exceptions("error.setting.unknownKey", HttpStatus.UNPROCESSABLE_ENTITY, key));
        OrgSetting row = find(org, branchId, schema.namespace(), def.key());
        if (row != null) {
            Map<String, Object> ch = new LinkedHashMap<>();
            ch.put("from", row.raw());
            ch.put("to", null);
            settings.delete(row);
            settings.flush();
            invalidateOrg(org);
            audit.record(new AuditService.Command(MODULE, "RESET", "OrgSetting", schema.namespace(), org, branchId,
                    Map.of(schema.namespace() + "." + def.key(), ch)));
        }
        return build(actor, schema, branchId);
    }

    // ---------------------------------------------------------------- reglas

    /**
     * Org: solo ORG_ADMIN (o acceso asistido de M22 D3 con ORG_SETTINGS en su alcance). Sede: ORG_ADMIN o el
     * ORG_BRANCH_ADMIN de esa sede (el alcance se comprueba aparte).
     */
    private void assertWriter(AuthenticatedActor actor, UUID branchId) {
        if (actor.actsAsOrgAdmin()) {
            return;
        }
        if (branchId != null && actor.role() == RoleType.ORG_BRANCH_ADMIN) {
            return;
        }
        throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
    }

    private Branch branch(AccessScope scope, UUID branchId) {
        return branches.findById(branchId)
                .filter(b -> b.getOrganizationId().equals(scope.organizationId()) && scope.canSeeBranch(b.getId()))  // [V15]
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private OrgSetting find(UUID org, UUID branchId, String ns, String key) {
        return (branchId == null
                ? settings.findByOrganizationIdAndBranchIdIsNullAndNamespaceAndKey(org, ns, key)
                : settings.findByOrganizationIdAndBranchIdAndNamespaceAndKey(org, branchId, ns, key)).orElse(null);
    }

    private SettingsSchema schema(String namespace) {
        return registry.find(namespace).orElseThrow(() -> new Exceptions("error.setting.unknownKey", HttpStatus.UNPROCESSABLE_ENTITY, namespace));
    }

    private boolean available(UUID orgId, SettingsSchema s) {
        if (s.moduleCode() == null) {
            return true;
        }
        AppModule m = modules.find(s.moduleCode());
        return m != null && (m.getKind() != ModuleKind.CONTRACTABLE || contractGate.enabled(orgId, s.moduleCode()));
    }

    private void assertAvailable(UUID orgId, SettingsSchema s) {
        if (!available(orgId, s)) {                                                                 // [V7]
            throw new Exceptions("error.common.moduleNotContracted", HttpStatus.FORBIDDEN);
        }
    }

    private SettingDtos.View build(AuthenticatedActor actor, SettingsSchema schema, UUID branchId) {
        boolean canEdit = actor.actsAsOrgAdmin() || (branchId != null && actor.role() == RoleType.ORG_BRANCH_ADMIN);
        List<SettingDtos.Item> items = new ArrayList<>();
        for (SettingKeyDef d : schema.keys()) {
            Resolved r = resolve(actor.organizationId(), branchId, schema.namespace(), d);
            items.add(new SettingDtos.Item(d.key(), d.type(), d.labelEs(), d.labelEn(), d.helpEs(), d.helpEn(), r.value(), r.source(),
                    d.branchOverridable(), d.min(), d.max(), d.values(), d.defaultValue(), r.platformDefault()));
        }
        return new SettingDtos.View(schema.namespace(), schema.moduleCode(), schema.titleEs(), schema.titleEn(), branchId, canEdit, items);
    }

    // ---------------------------------------------------------------- validación de valores (compartida con plataforma)

    /** Convierte y valida un valor contra la definición: tipo (400), rango o lista (422). */
    public static Object coerce(SettingKeyDef def, Object raw) {
        String label = "en".equals(LocaleContextHolder.getLocale().getLanguage()) ? def.labelEn() : def.labelEs();
        if (raw == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, label);
        }
        switch (def.type()) {
            case INT -> {
                long v;
                if (raw instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) {
                    v = n.longValue();
                } else if (raw instanceof String s && s.trim().matches("-?\\d{1,15}")) {
                    v = Long.parseLong(s.trim());
                } else {
                    throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, label);
                }
                range(def, label, v);
                return v;
            }
            case DECIMAL -> {
                double v;
                try {
                    v = raw instanceof Number n ? n.doubleValue() : new BigDecimal(raw.toString().trim()).doubleValue();
                } catch (NumberFormatException e) {
                    throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, label);
                }
                range(def, label, v);
                return v;
            }
            case BOOL -> {
                if (raw instanceof Boolean b) {
                    return b;
                }
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, label);
            }
            case STRING -> {
                String s = raw.toString().trim();
                range(def, label, s.length());
                return s;
            }
            case ENUM -> {
                String s = raw.toString().trim();
                if (def.values() == null || !def.values().contains(s)) {
                    throw new Exceptions("error.setting.notAllowed", HttpStatus.UNPROCESSABLE_ENTITY, label, String.join(", ", def.values() == null ? List.of() : def.values()));
                }
                return s;
            }
            default -> throw new IllegalStateException();
        }
    }

    private static void range(SettingKeyDef def, String label, double v) {
        if ((def.min() != null && v < def.min()) || (def.max() != null && v > def.max())) {
            throw new Exceptions("error.setting.outOfRange", HttpStatus.UNPROCESSABLE_ENTITY, label, fmt(def.min()), fmt(def.max()));
        }
    }

    private static String fmt(Double d) {
        return d == null ? "-" : (d == Math.rint(d) ? String.valueOf(d.longValue()) : String.valueOf(d));
    }
}
