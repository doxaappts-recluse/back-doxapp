package pe.dcs.app.features.hr.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.hr.dto.HrDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** M17 · Conceptos de planilla (HR_PAYROLL, org): ganancias/deducciones/aportes del empleador, fórmula fija/porcentaje/manual. [V4] porcentaje 0-100. */
@Service
@RequiredArgsConstructor
public class PayrollConceptService {

    private static final Set<String> KINDS = Set.of("EARNING", "DEDUCTION", "EMPLOYER_CONTRIBUTION");
    private static final Set<String> CALCS = Set.of("FIXED", "PERCENT_OF_BASE", "MANUAL");
    private static final String ENTITY = "PayrollConcept";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authz;
    private final AuditService audit;
    private final Clock clock;

    record Row(UUID id, String code, String nameEs, String kind, String calc, BigDecimal value) {
    }

    @Transactional(readOnly = true)
    public List<HrDtos.ConceptView> list(AccessScope scope, Boolean activeOnly) {
        MapSqlParameterSource ps = new MapSqlParameterSource("o", scope.organizationId());
        String w = " where organization_id = :o" + (Boolean.TRUE.equals(activeOnly) ? " and active = true" : "");
        return jdbc.query("select * from payroll_concept" + w + " order by sort_order, name_es", ps, (rs, i) -> view(rs));
    }

    List<Row> activeForCalc(UUID orgId) {
        return jdbc.query("select id, code, name_es, kind, calc, value from payroll_concept where organization_id = :o and active = true order by sort_order",
                new MapSqlParameterSource("o", orgId), (rs, i) -> new Row((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBigDecimal(6)));
    }

    @Transactional
    public HrDtos.ConceptView create(AuthenticatedActor actor, AccessScope scope, HrDtos.ConceptRequest r) {
        authz.require(actor, HrSupport.MOD_PAYROLL, Action.C);
        String code = HrSupport.trim(r == null ? null : r.code(), 30, "código");
        if (code == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "código");
        }
        code = code.toUpperCase();
        String nameEs = HrSupport.trim(r.nameEs(), 120, "nombre");
        String nameEn = HrSupport.hasText(r.nameEn()) ? HrSupport.trim(r.nameEn(), 120, "name") : nameEs;
        if (nameEs == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "nombre");
        }
        String kind = validKind(r.kind());
        String calc = validCalc(r.calc());
        BigDecimal value = validValue(calc, r.value());
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into payroll_concept (id, organization_id, code, name_es, name_en, kind, calc, value, mandatory, active, sort_order,"
                            + " created_at, created_by) values (:id, :o, :c, :nes, :nen, :k, :ca, :v, :m, :ac, :so, :at, :by)",
                    new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("c", code).addValue("nes", nameEs).addValue("nen", nameEn)
                            .addValue("k", kind).addValue("ca", calc).addValue("v", value).addValue("m", Boolean.TRUE.equals(r.mandatory()))
                            .addValue("ac", r.active() == null || r.active()).addValue("so", r.sortOrder() == null ? 0 : r.sortOrder())
                            .addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()));
        } catch (DuplicateKeyException e) {
            throw new Exceptions("error.hr.conceptInvalid", HttpStatus.CONFLICT);
        }
        audit.record(new AuditService.Command(HrSupport.MOD_PAYROLL, "CREATE", ENTITY, id, scope.organizationId(), null, Map.of("code", code)));
        return get(scope, id);
    }

    @Transactional
    public HrDtos.ConceptView update(AuthenticatedActor actor, AccessScope scope, UUID id, HrDtos.ConceptRequest r) {
        authz.require(actor, HrSupport.MOD_PAYROLL, Action.E);
        get(scope, id);
        String nameEs = HrSupport.trim(r.nameEs(), 120, "nombre");
        String nameEn = HrSupport.hasText(r.nameEn()) ? HrSupport.trim(r.nameEn(), 120, "name") : nameEs;
        String calc = validCalc(r.calc());
        BigDecimal value = validValue(calc, r.value());
        int n = jdbc.update("update payroll_concept set name_es = :nes, name_en = :nen, kind = :k, calc = :ca, value = :v, mandatory = :m, active = :ac,"
                        + " sort_order = :so, updated_at = :at, updated_by = :by, version = version + 1"
                        + " where id = :id and organization_id = :o and (cast(:v0 as bigint) is null or version = :v0)",
                new MapSqlParameterSource("nes", nameEs).addValue("nen", nameEn).addValue("k", validKind(r.kind())).addValue("ca", calc).addValue("v", value)
                        .addValue("m", Boolean.TRUE.equals(r.mandatory())).addValue("ac", r.active() == null || r.active()).addValue("so", r.sortOrder() == null ? 0 : r.sortOrder())
                        .addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", id).addValue("o", scope.organizationId())
                        .addValue("v0", r.version()));
        if (n == 0) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        audit.record(new AuditService.Command(HrSupport.MOD_PAYROLL, "UPDATE", ENTITY, id, scope.organizationId(), null, Map.of()));
        return get(scope, id);
    }

    @Transactional
    public void delete(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, HrSupport.MOD_PAYROLL, Action.E);
        get(scope, id);
        Integer n = jdbc.queryForObject("select count(*) from payroll_record_line where concept_id = :id", new MapSqlParameterSource("id", id), Integer.class);
        if (n != null && n > 0) {
            throw new Exceptions("error.common.hasDependencies", HttpStatus.CONFLICT);
        }
        jdbc.update("delete from payroll_concept where id = :id and organization_id = :o", new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()));
        audit.record(new AuditService.Command(HrSupport.MOD_PAYROLL, "DELETE", ENTITY, id, scope.organizationId(), null, Map.of()));
    }

    @Transactional(readOnly = true)
    public HrDtos.ConceptView get(AccessScope scope, UUID id) {
        return jdbc.query("select * from payroll_concept where id = :id and organization_id = :o",
                        new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()), (rs, i) -> view(rs)).stream()
                .findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private static String validKind(String s) {
        String t = s == null ? null : s.trim().toUpperCase();
        if (t == null || !KINDS.contains(t)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo");
        }
        return t;
    }

    private static String validCalc(String s) {
        String t = s == null ? null : s.trim().toUpperCase();
        if (t == null || !CALCS.contains(t)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "cálculo");
        }
        return t;
    }

    private static BigDecimal validValue(String calc, String raw) {
        if ("MANUAL".equals(calc)) {
            return HrSupport.optionalAmount(raw, "valor");
        }
        BigDecimal v = HrSupport.requiredAmount(raw, "valor");                                                              // [V4]
        if ("PERCENT_OF_BASE".equals(calc) && (v.compareTo(BigDecimal.ZERO) < 0 || v.compareTo(new BigDecimal("100")) > 0)) {
            throw new Exceptions("error.hr.conceptInvalid", HttpStatus.BAD_REQUEST);
        }
        return v;
    }

    private static HrDtos.ConceptView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        BigDecimal v = rs.getBigDecimal("value");
        return new HrDtos.ConceptView((UUID) rs.getObject("id"), rs.getString("code"), rs.getString("name_es"), rs.getString("name_en"), rs.getString("kind"),
                rs.getString("calc"), v == null ? null : v.toPlainString(), rs.getBoolean("mandatory"), rs.getBoolean("active"), rs.getInt("sort_order"), rs.getLong("version"));
    }
}
