package pe.dcs.app.features.audit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.dcs.app.features.audit.dto.PurgeResult;
import pe.dcs.app.features.audit.dto.RetentionRequest;
import pe.dcs.app.features.audit.dto.RetentionView;
import pe.dcs.app.features.module.domain.AppModule;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.ModuleCatalog;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M22 · retención de la auditoría (V6, V12). Cada organización puede alargar la retención de todo o de un módulo, nunca
 * bajar del mínimo legal (12 meses en general, 60 en finanzas). La purga es el único camino de borrado y queda registrada.
 * Prioridad: módulo de la organización → toda la organización → valor de plataforma → 24 meses; el mínimo legal manda siempre.
 */
@Slf4j
@Service
public class RetentionService {

    public static final int GENERAL_MINIMUM = 12;
    public static final int FINANCE_MINIMUM = 60;
    private static final int MAX_MONTHS = 240;

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final ModuleCatalog catalog;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int defaultMonths;

    public RetentionService(JdbcTemplate jdbc, AuditService audit, ModuleCatalog catalog, PlatformTransactionManager txm, Clock clock,
                            @Value("${audit.retention.default-months:24}") int defaultMonths) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.catalog = catalog;
        this.tx = new TransactionTemplate(txm);
        this.clock = clock;
        this.defaultMonths = defaultMonths;
    }

    /** Mínimo legal de un módulo: finanzas 60 meses, el resto 12. */
    public static int legalMinimum(String moduleCode) {
        return moduleCode != null && (moduleCode.startsWith("FIN_") || moduleCode.startsWith("ONLINE_")) ? FINANCE_MINIMUM : GENERAL_MINIMUM;
    }

    // ------------------------------------------------------------------ consulta

    @Transactional(readOnly = true)
    public RetentionView viewOrg(AuthenticatedActor actor) {
        requireOrgAdmin(actor);
        return view(actor.organizationId());
    }

    @Transactional(readOnly = true)
    public RetentionView viewPlatform(AuthenticatedActor actor) {
        requireSystemAdmin(actor);
        return view(null);
    }

    private RetentionView view(UUID orgId) {
        Map<String, Integer> byModule = new HashMap<>();
        Integer orgWide = null;
        if (orgId != null) {
            for (Map<String, Object> r : jdbc.queryForList("select module_code, retain_months from retention_policy where organization_id = ?", orgId)) {
                String code = (String) r.get("module_code");
                if (code == null) {
                    orgWide = (Integer) r.get("retain_months");
                } else {
                    byModule.put(code, (Integer) r.get("retain_months"));
                }
            }
        }
        Integer platform = platformMonths();
        int base = platform != null ? platform : defaultMonths;
        int orgEffective = orgWide != null ? orgWide : base;

        List<RetentionView.ModuleRetention> modules = new ArrayList<>();
        for (AppModule m : catalog.published()) {
            if (m.getLevels() == null || java.util.Arrays.stream(m.getLevels()).noneMatch(l -> l.equals("N2") || l.equals("N3"))) {
                continue;
            }
            int min = legalMinimum(m.getCode());
            Integer configured = byModule.get(m.getCode());
            String source = configured != null ? "MODULE" : orgWide != null ? "ORG" : platform != null ? "PLATFORM" : "DEFAULT";
            int effective = Math.max(configured != null ? configured : orgEffective, min);
            modules.add(new RetentionView.ModuleRetention(m.getCode(), m.getNameEs(), m.getNameEn(), min, configured, effective, source));
        }
        return new RetentionView(defaultMonths, platform, orgWide, Math.max(orgEffective, GENERAL_MINIMUM), GENERAL_MINIMUM, modules);
    }

    private Integer platformMonths() {
        List<Integer> l = jdbc.queryForList("select retain_months from retention_policy where organization_id is null and module_code is null", Integer.class);
        return l.isEmpty() ? null : l.get(0);
    }

    /** Meses de retención vigentes para (organización, módulo), ya con el mínimo legal aplicado. */
    public int effectiveMonths(UUID orgId, String moduleCode) {
        Integer months = null;
        if (orgId != null) {
            List<Integer> l = jdbc.queryForList("select retain_months from retention_policy where organization_id = ? and module_code = ?", Integer.class, orgId, moduleCode);
            if (!l.isEmpty()) {
                months = l.get(0);
            } else {
                l = jdbc.queryForList("select retain_months from retention_policy where organization_id = ? and module_code is null", Integer.class, orgId);
                if (!l.isEmpty()) {
                    months = l.get(0);
                }
            }
        }
        if (months == null) {
            Integer p = platformMonths();
            months = p != null ? p : defaultMonths;
        }
        return Math.max(months, legalMinimum(moduleCode));
    }

    // ------------------------------------------------------------------ configuración

    @Transactional
    public RetentionView setOrg(AuthenticatedActor actor, RetentionRequest req) {
        requireOrgAdmin(actor);
        upsert(actor.organizationId(), req);
        return view(actor.organizationId());
    }

    @Transactional
    public RetentionView clearOrg(AuthenticatedActor actor, String moduleCode) {
        requireOrgAdmin(actor);
        UUID org = actor.organizationId();
        if (moduleCode != null && catalog.find(moduleCode) == null) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        Integer previous = current(org, moduleCode);
        if (previous != null) {
            jdbc.update("delete from retention_policy where organization_id = ? and module_code is not distinct from ?", org, moduleCode);
            record(org, moduleCode, previous, null);
        }
        return view(org);
    }

    @Transactional
    public RetentionView setPlatform(AuthenticatedActor actor, RetentionRequest req) {
        requireSystemAdmin(actor);
        upsert(null, new RetentionRequest(null, req == null ? null : req.retainMonths()));
        return view(null);
    }

    private void upsert(UUID orgId, RetentionRequest req) {
        if (req == null || req.retainMonths() == null) {
            throw new Exceptions("error.retention.monthsRequired", HttpStatus.BAD_REQUEST);
        }
        int months = req.retainMonths();
        String module = req.moduleCode() == null || req.moduleCode().isBlank() ? null : req.moduleCode().trim();
        if (module != null && catalog.find(module) == null) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        if (months > MAX_MONTHS) {
            throw new Exceptions("error.retention.tooLong", HttpStatus.BAD_REQUEST, MAX_MONTHS);
        }
        int min = module == null ? GENERAL_MINIMUM : legalMinimum(module);
        if (months < min) {
            throw new Exceptions("error.retention.belowMinimum", HttpStatus.UNPROCESSABLE_ENTITY, min);
        }
        Integer previous = current(orgId, module);
        if (previous != null) {
            jdbc.update("update retention_policy set retain_months = ?, updated_at = ?, version = version + 1 where organization_id is not distinct from ? and module_code is not distinct from ?",
                    months, Timestamp.from(clock.instant()), orgId, module);
        } else {
            jdbc.update("insert into retention_policy (id, organization_id, module_code, retain_months, created_at) values (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), orgId, module, months, Timestamp.from(clock.instant()));
        }
        if (previous == null || previous != months) {
            record(orgId, module, previous, months);
        }
    }

    private Integer current(UUID orgId, String module) {
        List<Integer> l = jdbc.queryForList("select retain_months from retention_policy where organization_id is not distinct from ? and module_code is not distinct from ?",
                Integer.class, orgId, module);
        return l.isEmpty() ? null : l.get(0);
    }

    private void record(UUID orgId, String module, Integer from, Integer to) {
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("moduleCode", module);
        diff.put("from", from);
        diff.put("to", to);
        audit.record(new AuditService.Command("AUDIT_LOG", "RETENTION_SET", "RetentionPolicy", orgId, orgId, null, diff));
    }

    // ------------------------------------------------------------------ purga

    /** Ejecución manual de plataforma (misma lógica que el proceso diario). */
    public PurgeResult runPlatform(AuthenticatedActor actor) {
        requireSystemAdmin(actor);
        return purge();
    }

    /**
     * Borra los eventos que superaron su retención, por (organización, módulo), con la función audit_purge (único borrado
     * permitido por el trigger) y registra cada purga en la auditoría.
     */
    public PurgeResult purge() {
        Instant now = clock.instant();
        Instant floor = now.atZone(ZoneOffset.UTC).minusMonths(GENERAL_MINIMUM).toInstant();
        List<Map<String, Object>> groups = jdbc.queryForList(
                "select distinct organization_id, module_code from audit_event where at < ?", Timestamp.from(floor));
        long total = 0;
        int touched = 0;
        for (Map<String, Object> g : groups) {
            UUID org = (UUID) g.get("organization_id");
            String module = (String) g.get("module_code");
            int months = effectiveMonths(org, module);
            Instant before = now.atZone(ZoneOffset.UTC).minusMonths(months).toInstant();
            Long deleted = tx.execute(status -> {
                Long n = jdbc.queryForObject("select audit_purge(?::uuid, ?::varchar, ?::timestamptz)", Long.class, org, module, Timestamp.from(before));
                if (n != null && n > 0) {
                    Map<String, Object> diff = new LinkedHashMap<>();
                    diff.put("moduleCode", module);
                    diff.put("retainMonths", months);
                    diff.put("before", before.toString());
                    diff.put("deleted", n);
                    audit.record(new AuditService.Command("AUDIT_LOG", "PURGE", "AuditEvent", null, org, null, diff));
                }
                return n;
            });
            if (deleted != null && deleted > 0) {
                total += deleted;
                touched++;
            }
        }
        if (total > 0) {
            log.info("Purga de auditoría: {} eventos en {} grupos", total, touched);
        }
        return new PurgeResult(total, touched);
    }

    private void requireOrgAdmin(AuthenticatedActor actor) {
        if (actor.role() != RoleType.ORG_ADMIN) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
    }

    private void requireSystemAdmin(AuthenticatedActor actor) {
        if (actor.role() != RoleType.SYSTEM_ADMIN) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
    }
}
