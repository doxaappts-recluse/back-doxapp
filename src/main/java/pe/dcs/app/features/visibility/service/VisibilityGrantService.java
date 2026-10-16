package pe.dcs.app.features.visibility.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.approval.dto.ApprovalDtos;
import pe.dcs.app.features.approval.service.ApprovalEngine;
import pe.dcs.app.features.approval.service.ApprovalHandler;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
import pe.dcs.app.features.person.service.PersonLookupService;
import pe.dcs.app.features.visibility.dto.VisibilityDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.vo.DocumentValidator;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M21 · Autorizaciones de visibilidad entre sedes. La sede X pide ver a una persona de la sede Y en un módulo con regla APPROVAL_REQUIRED;
 * decide la sede Y (motor de aprobaciones) y, aprobada, queda un {@code visibility_grant} hasta {@code visible_until}. Vence solo; la sede Y
 * o el administrador de la organización pueden revocarla en cualquier momento (efecto inmediato: los módulos consultan la tabla en cada petición).
 */
@Service
@RequiredArgsConstructor
public class VisibilityGrantService {

    public static final String MODULE = "VISIBILITY_RULES";
    private static final String ENTITY = "VisibilityGrant";

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final AuditService audit;
    private final AuthorizationService authz;
    private final ApprovalEngine engine;
    private final DataAccessRuleService rules;
    private final PersonLookupService lookup;
    private final NotificationService notifications;

    @Value("${visibility.max-days:90}")
    private int maxDays;

    // ---------------------------------------------------------------- consulta
    @Transactional(readOnly = true)
    public PageResponse<VisibilityDtos.Grant> search(AuthenticatedActor actor, AccessScope scope, VisibilityDtos.Search req) {
        return search(actor, scope, req, null);
    }

    private PageResponse<VisibilityDtos.Grant> search(AuthenticatedActor actor, AccessScope scope, VisibilityDtos.Search req, UUID onlyId) {
        VisibilityDtos.Search.Filters f = req == null || req.filters() == null ? new VisibilityDtos.Search.Filters(null, null, null) : req.filters();
        LocalDate today = LocalDate.now(clock);
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId()).addValue("today", Date.valueOf(today));
        StringBuilder w = new StringBuilder("g.organization_id = :org");
        if (!scope.allBranches()) {
            ps.addValue("sb", scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds()));
            w.append(" and (g.source_branch_id in (:sb) or g.target_branch_id in (:sb))");
        }
        if (onlyId != null) {
            w.append(" and g.id = :oid");
            ps.addValue("oid", onlyId);
        }
        if (f.status() != null && !f.status().isBlank()) {
            switch (f.status().trim().toUpperCase()) {
                case "ACTIVE" -> w.append(" and g.active and g.visible_until >= :today");
                case "EXPIRED" -> w.append(" and g.revoked_at is null and (not g.active or g.visible_until < :today)");
                case "REVOKED" -> w.append(" and g.revoked_at is not null");
                default -> throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
            }
        }
        if (f.branchId() != null) {
            w.append(" and (g.source_branch_id = :fb or g.target_branch_id = :fb)");
            ps.addValue("fb", f.branchId());
        }
        if (f.moduleCode() != null && !f.moduleCode().isBlank()) {
            w.append(" and g.module_code = :fm");
            ps.addValue("fm", f.moduleCode().trim().toUpperCase());
        }
        String from = """
                from visibility_grant g join person p on p.id = g.person_id join branch sb on sb.id = g.source_branch_id join branch tb on tb.id = g.target_branch_id
                  left join person ap on ap.id = g.approved_by left join person rp on rp.id = g.revoked_by
                where\s""" + w;
        Long total = jdbc.queryForObject("select count(*) " + from, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 100));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        boolean canA = authz.effectiveActions(actor, MODULE).contains(Action.A.name());
        List<VisibilityDtos.Grant> rows = jdbc.query("""
                select g.id, g.person_id, p.first_name, p.last_name, g.source_branch_id, sb.name as sname, g.target_branch_id, tb.name as tname, g.module_code,
                       g.visible_until, g.active, g.revoked_at, g.approved_at, ap.first_name as afn, ap.last_name as aln, rp.first_name as rfn, rp.last_name as rln,
                       g.revoke_reason\s""" + from + " order by g.created_at desc limit :lim offset :off", ps, (rs, i) -> {
            LocalDate until = rs.getDate("visible_until").toLocalDate();
            boolean revoked = rs.getTimestamp("revoked_at") != null;
            boolean live = rs.getBoolean("active") && !until.isBefore(today);
            String status = revoked ? "REVOKED" : live ? "ACTIVE" : "EXPIRED";
            UUID source = (UUID) rs.getObject("source_branch_id");
            boolean reveal = scope.canSeeBranch(source) || live;
            return new VisibilityDtos.Grant((UUID) rs.getObject("id"), (UUID) rs.getObject("person_id"), reveal ? rs.getString("first_name") + " " + rs.getString("last_name") : null,
                    source, rs.getString("sname"), (UUID) rs.getObject("target_branch_id"), rs.getString("tname"), rs.getString("module_code"), until, status,
                    rs.getString("afn") == null ? null : rs.getString("afn") + " " + rs.getString("aln"), instant(rs.getTimestamp("approved_at")),
                    rs.getString("rfn") == null ? null : rs.getString("rfn") + " " + rs.getString("rln"), instant(rs.getTimestamp("revoked_at")), rs.getString("revoke_reason"),
                    live && canA && scope.canSeeBranch(source));
        });
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    // ---------------------------------------------------------------- solicitud
    @Transactional
    public ApprovalDtos.Summary request(AuthenticatedActor actor, AccessScope scope, VisibilityDtos.AccessRequest r) {
        if (r == null || r.targetBranchId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sede solicitante");
        }
        String module = r.moduleCode() == null ? "" : r.moduleCode().trim().toUpperCase();
        if (!rules.isAware(module) || !"APPROVAL_REQUIRED".equals(rules.effectiveScope(scope.organizationId(), module))) {
            throw new Exceptions("error.visibility.moduleNotEligible", HttpStatus.UNPROCESSABLE_ENTITY);            // [V10]
        }
        String targetName = branchName(scope.organizationId(), r.targetBranchId(), true);
        if (targetName == null || !scope.canSeeBranch(r.targetBranchId())) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        LocalDate today = LocalDate.now(clock);
        if (r.visibleUntil() == null || r.visibleUntil().isBefore(today) || r.visibleUntil().isAfter(today.plusDays(maxDays))) {
            throw new Exceptions("error.visibility.dateInvalid", HttpStatus.UNPROCESSABLE_ENTITY, maxDays);        // [V9]
        }
        String doc = r.docNumber() == null ? null : DocumentValidator.normalize(r.docNumber());
        if (r.docType() == null || doc == null || doc.isBlank()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "documento");
        }
        PersonLookupService.PersonMin p = lookup.find(scope.organizationId(), r.docType(), doc).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        List<UUID> sourceRows = jdbc.query("select primary_branch_id from person where id = :id and status <> 'MERGED' and anonymized_at is null",
                new MapSqlParameterSource("id", p.id()), (rs, i) -> (UUID) rs.getObject(1));
        UUID source = sourceRows.isEmpty() ? null : sourceRows.get(0);
        if (source == null) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        if (source.equals(r.targetBranchId())) {
            throw new Exceptions("error.visibility.sameBranch", HttpStatus.UNPROCESSABLE_ENTITY);                  // [V11]
        }
        Boolean dup = jdbc.queryForObject("""
                select exists (select 1 from visibility_grant where person_id = :p and target_branch_id = :t and module_code = :m and active and visible_until >= :today)
                    or exists (select 1 from approval_request where type = 'VISIBILITY_ACCESS' and status = 'PENDING' and subject_id = :p and related_branch_id = :t
                               and payload ->> 'moduleCode' = :m)""", new MapSqlParameterSource("p", p.id()).addValue("t", r.targetBranchId()).addValue("m", module)
                .addValue("today", Date.valueOf(today)), Boolean.class);
        if (Boolean.TRUE.equals(dup)) {
            throw new Exceptions("error.visibility.openExists", HttpStatus.CONFLICT);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("moduleCode", module);
        payload.put("visibleUntil", r.visibleUntil().toString());
        payload.put("targetBranchName", targetName);
        payload.put("sourceBranchName", branchName(scope.organizationId(), source, false));
        UUID id = engine.open(new ApprovalEngine.NewRequest(scope.organizationId(), source, r.targetBranchId(), "VISIBILITY_ACCESS", "PERSON", p.id(),
                scope.personId(), r.reason(), payload));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("module", module);
        d.put("target", targetName);
        d.put("until", r.visibleUntil().toString());
        audit.record(new AuditService.Command(MODULE, "REQUEST", "ApprovalRequest", id, scope.organizationId(), r.targetBranchId(), d));
        return engine.get(actor, scope, id).request();
    }

    // ---------------------------------------------------------------- lo que llama el handler del motor
    /** Aprobada la solicitud, otorga la visibilidad. Revalida la regla y la fecha (pudieron cambiar mientras esperaba). */
    @Transactional
    public void grantFromRequest(ApprovalHandler.ApprovalRow row, UUID approver) {
        String module = String.valueOf(row.payload().get("moduleCode"));
        LocalDate until = LocalDate.parse(String.valueOf(row.payload().get("visibleUntil")));
        LocalDate today = LocalDate.now(clock);
        if (!"APPROVAL_REQUIRED".equals(rules.effectiveScope(row.organizationId(), module))) {
            throw new Exceptions("error.visibility.moduleNotEligible", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (until.isBefore(today)) {
            throw new Exceptions("error.visibility.dateInvalid", HttpStatus.UNPROCESSABLE_ENTITY, maxDays);
        }
        Timestamp now = Timestamp.from(clock.instant());
        MapSqlParameterSource ps = new MapSqlParameterSource("org", row.organizationId()).addValue("p", row.subjectId()).addValue("s", row.branchId())
                .addValue("t", row.relatedBranchId()).addValue("m", module).addValue("req", row.id()).addValue("until", Date.valueOf(until))
                .addValue("by", approver).addValue("now", now);
        int updated = jdbc.update("update visibility_grant set visible_until = greatest(visible_until, :until), request_id = :req, approved_by = :by, approved_at = :now"
                + " where person_id = :p and target_branch_id = :t and module_code = :m and active", ps);
        UUID gid = UUID.randomUUID();
        if (updated == 0) {
            try {
                jdbc.update("""
                        insert into visibility_grant (id, organization_id, person_id, source_branch_id, target_branch_id, module_code, request_id, visible_until, active,
                            approved_by, approved_at, created_at) values (:id, :org, :p, :s, :t, :m, :req, :until, true, :by, :now, :now)""", ps.addValue("id", gid));
            } catch (DataIntegrityViolationException e) {
                throw new Exceptions("error.visibility.openExists", HttpStatus.CONFLICT);
            }
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("module", module);
        d.put("until", until.toString());
        d.put("target", String.valueOf(row.payload().get("targetBranchName")));
        audit.record(new AuditService.Command(MODULE, "GRANT", ENTITY, gid, row.organizationId(), row.branchId(), d));
    }

    // ---------------------------------------------------------------- revocar y vencer
    @Transactional
    public VisibilityDtos.Grant revoke(AuthenticatedActor actor, AccessScope scope, UUID id, VisibilityDtos.RevokeRequest r) {
        List<Object[]> g = jdbc.query("select source_branch_id, target_branch_id, module_code, active, visible_until, revoked_at from visibility_grant where id = :id and organization_id = :o for update",
                new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()),
                (rs, i) -> new Object[]{rs.getObject(1), rs.getObject(2), rs.getString(3), rs.getBoolean(4), rs.getDate(5).toLocalDate(), rs.getTimestamp(6)});
        if (g.isEmpty() || !(scope.canSeeBranch((UUID) g.get(0)[0]) || scope.canSeeBranch((UUID) g.get(0)[1]))) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        Object[] x = g.get(0);
        UUID source = (UUID) x[0];
        UUID target = (UUID) x[1];
        if (!scope.canSeeBranch(source)) {
            throw new Exceptions("error.approval.notAllowed", HttpStatus.FORBIDDEN);                                // solo la sede dueña o el administrador
        }
        if (!(boolean) x[3] || x[5] != null) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, x[5] != null ? "REVOKED" : "EXPIRED");
        }
        String reason = r == null || r.reason() == null || r.reason().isBlank() ? null : r.reason().trim();
        if (reason != null && reason.length() > 300) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "motivo");
        }
        jdbc.update("update visibility_grant set active = false, revoked_by = :by, revoked_at = :now, revoke_reason = :r where id = :id",
                new MapSqlParameterSource("by", scope.personId()).addValue("now", Timestamp.from(clock.instant())).addValue("r", reason).addValue("id", id));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("module", x[2]);
        if (reason != null) {
            d.put("reason", reason);
        }
        audit.record(new AuditService.Command(MODULE, "REVOKE", ENTITY, id, scope.organizationId(), source, d));
        Map<String, String> params = new HashMap<>();
        params.put("branch", branchName(scope.organizationId(), source, false));
        notifications.toPersons(NotificationType.VISIBILITY_REVOKED, scope.organizationId(), notifications.branchAdmins(scope.organizationId(), target), params,
                "/app/visibility", null);
        return search(actor, scope, null, id).getContent().get(0);
    }

    /** Job [V12]: cierra las autorizaciones cuya fecha límite pasó y avisa a la sede que las tenía. */
    @Transactional
    public int expireDue() {
        List<Object[]> due = jdbc.query("select id, organization_id, source_branch_id, target_branch_id from visibility_grant where active and visible_until < :today for update",
                new MapSqlParameterSource("today", Date.valueOf(LocalDate.now(clock))),
                (rs, i) -> new Object[]{rs.getObject(1), rs.getObject(2), rs.getObject(3), rs.getObject(4)});
        Timestamp now = Timestamp.from(clock.instant());
        for (Object[] d : due) {
            UUID org = (UUID) d[1];
            jdbc.update("update visibility_grant set active = false, expired_at = :now where id = :id", new MapSqlParameterSource("now", now).addValue("id", d[0]));
            Map<String, String> params = new HashMap<>();
            params.put("branch", branchName(org, (UUID) d[2], false));
            notifications.toPersons(NotificationType.VISIBILITY_EXPIRED, org, notifications.branchAdmins(org, (UUID) d[3]), params, "/app/visibility", "grant-exp:" + d[0]);
            audit.record(new AuditService.Command(MODULE, "EXPIRE", ENTITY, d[0], org, (UUID) d[2], new LinkedHashMap<>()));
        }
        return due.size();
    }

    private String branchName(UUID orgId, UUID branchId, boolean mustBeActive) {
        return jdbc.query("select name from branch where id = :id and organization_id = :o" + (mustBeActive ? " and status = 'ACTIVE'" : ""),
                new MapSqlParameterSource("id", branchId).addValue("o", orgId), (rs, i) -> rs.getString(1)).stream().findFirst().orElse(null);
    }

    private static java.time.Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

}
