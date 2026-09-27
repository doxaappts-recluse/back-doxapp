package pe.dcs.app.features.approval.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.dcs.app.features.approval.dto.ApprovalDtos;
import pe.dcs.app.features.approval.service.ApprovalHandler.ApprovalRow;
import pe.dcs.app.features.approval.service.ApprovalHandler.Decision;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Núcleo 01 §3 · motor único de aprobaciones. Guarda las solicitudes, controla quién puede decidir y aplica las reglas comunes:
 * [V1] quien solicita no decide lo suyo, [V2] rechazar exige motivo, [V3] la decisión es única e idempotente (409), [V4] decidir exige
 * la acción A del módulo dueño más el alcance de la sede, [V5] cancela solo el solicitante en PENDING, [V6] expiración opcional por tipo.
 * Los módulos registran un {@link ApprovalHandler}; el motor no conoce sus reglas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalEngine {

    public static final int BULK_MAX = 50;
    private static final TypeReference<Map<String, Object>> PAYLOAD = new TypeReference<>() { };
    private static final int MAX_TEXT = 1000;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final AuditService audit;
    private final AuthorizationService authz;
    private final NotificationService notifications;
    private final ObjectProvider<ApprovalHandler> handlerProvider;
    private final PlatformTransactionManager txm;

    private volatile Map<String, ApprovalHandler> handlers;

    // ---------------------------------------------------------------- registro
    private Map<String, ApprovalHandler> handlers() {
        Map<String, ApprovalHandler> h = handlers;
        if (h == null) {
            h = handlerProvider.orderedStream().collect(Collectors.toMap(ApprovalHandler::type, x -> x, (a, b) -> a, LinkedHashMap::new));
            handlers = h;
        }
        return h;
    }

    public ApprovalHandler handler(String type) {
        ApprovalHandler h = handlers().get(type);
        if (h == null) {
            throw new IllegalStateException("Tipo de solicitud sin handler: " + type);
        }
        return h;
    }

    // ---------------------------------------------------------------- alta (la llama el módulo dueño dentro de su transacción)
    public record NewRequest(UUID orgId, UUID branchId, UUID relatedBranchId, String type, String subjectType, UUID subjectId, UUID requestedBy,
                             String reason, Map<String, Object> payload) {
    }

    @Transactional
    public UUID open(NewRequest r) {
        ApprovalHandler h = handler(r.type());
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        Integer days = h.expiryDays();
        jdbc.update("""
                insert into approval_request (id, organization_id, branch_id, related_branch_id, type, subject_type, subject_id, requested_by, status,
                    reason, expires_at, payload, created_at, created_by, version)
                values (:id, :org, :br, :rel, :type, :st, :sid, :by, 'PENDING', :reason, :exp, cast(:payload as jsonb), :now, :by, 0)
                """, new MapSqlParameterSource("id", id).addValue("org", r.orgId()).addValue("br", r.branchId()).addValue("rel", r.relatedBranchId())
                .addValue("type", r.type()).addValue("st", r.subjectType()).addValue("sid", r.subjectId()).addValue("by", r.requestedBy())
                .addValue("reason", cut(r.reason(), 500)).addValue("exp", days == null ? null : Timestamp.from(now.plus(Duration.ofDays(days))))
                .addValue("payload", json(r.payload())).addValue("now", Timestamp.from(now)));
        ApprovalRow row = new ApprovalRow(id, r.orgId(), r.branchId(), r.relatedBranchId(), r.type(), r.subjectType(), r.subjectId(), r.requestedBy(),
                "PENDING", r.reason(), r.payload() == null ? Map.of() : r.payload());
        List<UUID> deciders = new ArrayList<>(notifications.branchAdmins(r.orgId(), r.branchId()));
        deciders.remove(r.requestedBy());
        notify(h, "REQUESTED", row, deciders);
        return id;
    }

    // ---------------------------------------------------------------- consulta
    @Transactional(readOnly = true)
    public PageResponse<ApprovalDtos.Summary> search(AuthenticatedActor actor, AccessScope scope, ApprovalDtos.Search req) {
        ApprovalDtos.Search.Filters f = req == null || req.filters() == null ? new ApprovalDtos.Search.Filters(null, null, null, null, null, null) : req.filters();
        Set<String> decidable = decidableTypes(actor);
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId()).addValue("me", scope.personId());
        StringBuilder w = new StringBuilder("r.organization_id = :org and ").append(visible(scope, ps));
        if (f.type() != null && !f.type().isBlank()) {
            w.append(" and r.type = :type");
            ps.addValue("type", f.type().trim().toUpperCase());
        }
        if (f.status() != null && !f.status().isBlank()) {
            String st = f.status().trim().toUpperCase();
            if (!Set.of("PENDING", "APPROVED", "REJECTED", "CANCELLED", "EXPIRED").contains(st)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
            }
            w.append(" and r.status = :status");
            ps.addValue("status", st);
        }
        if (f.branchId() != null) {
            w.append(" and (r.branch_id = :fb or r.related_branch_id = :fb)");
            ps.addValue("fb", f.branchId());
        }
        if (f.olderThanDays() != null && f.olderThanDays() > 0) {
            w.append(" and r.status = 'PENDING' and r.created_at <= :older");
            ps.addValue("older", Timestamp.from(clock.instant().minus(Duration.ofDays(f.olderThanDays()))));
        }
        if (Boolean.TRUE.equals(f.mine())) {
            w.append(" and r.requested_by = :me");
        }
        if (Boolean.TRUE.equals(f.decidable())) {
            if (decidable.isEmpty()) {
                w.append(" and false");
            } else {
                w.append(" and r.status = 'PENDING' and r.requested_by <> :me and r.type in (:dtypes)");
                ps.addValue("dtypes", decidable);
                if (!scope.allBranches()) {
                    w.append(" and r.branch_id in (:dbranches)");
                    ps.addValue("dbranches", branchList(scope));
                }
            }
        }
        String from = FROM + " where " + w;
        Long total = jdbc.queryForObject("select count(*) " + from, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 100));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<ApprovalDtos.Summary> rows = jdbc.query(SELECT + from + " order by (r.status = 'PENDING') desc, r.created_at desc limit :lim offset :off", ps,
                (rs, i) -> summary(rs, scope, decidable));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    @Transactional(readOnly = true)
    public ApprovalDtos.Detail get(AuthenticatedActor actor, AccessScope scope, UUID id) {
        ApprovalDtos.Summary s = loadSummary(actor, scope, id);
        List<ApprovalDtos.Comment> comments = jdbc.query("""
                select c.id, c.author_id, p.first_name, p.last_name, c.kind, c.body, c.created_at
                from approval_comment c join person p on p.id = c.author_id where c.request_id = :id order by c.created_at, c.id
                """, new MapSqlParameterSource("id", id), (rs, i) -> new ApprovalDtos.Comment((UUID) rs.getObject(1), (UUID) rs.getObject(2),
                rs.getString(3) + " " + rs.getString(4), s.requestedById().equals(rs.getObject(2)), rs.getString(5), rs.getString(6),
                rs.getTimestamp(7).toInstant()));
        return new ApprovalDtos.Detail(s, comments);
    }

    public List<ApprovalDtos.TypeInfo> types(AuthenticatedActor actor) {
        Set<String> ok = decidableTypes(actor);
        return handlers().values().stream().map(h -> new ApprovalDtos.TypeInfo(h.type(), h.moduleCode(), ok.contains(h.type()))).toList();
    }

    // ---------------------------------------------------------------- decisiones
    @Transactional
    public ApprovalDtos.Summary approve(AuthenticatedActor actor, AccessScope scope, UUID id, String note) {
        decide(actor, scope, id, true, note);
        return loadSummary(actor, scope, id);
    }

    @Transactional
    public ApprovalDtos.Summary reject(AuthenticatedActor actor, AccessScope scope, UUID id, String reason) {
        decide(actor, scope, id, false, reason);
        return loadSummary(actor, scope, id);
    }

    private void decide(AuthenticatedActor actor, AccessScope scope, UUID id, boolean approve, String text) {
        String note = trim(text);
        if (!approve && note == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "motivo");                     // [V2]
        }
        ApprovalRow row = lock(scope, id);
        if (!"PENDING".equals(row.status())) {
            throw new Exceptions("error.common.alreadyDecided", HttpStatus.CONFLICT);                              // [V3]
        }
        if (row.requestedBy().equals(scope.personId())) {
            throw new Exceptions("error.common.selfApproval", HttpStatus.FORBIDDEN);                               // [V1]
        }
        ApprovalHandler h = handler(row.type());
        authz.require(actor, h.moduleCode(), Action.A);                                                            // [V4]
        if (!scope.canSeeBranch(row.branchId())) {
            throw new Exceptions("error.approval.notAllowed", HttpStatus.FORBIDDEN);
        }
        apply(h, row, approve ? "APPROVED" : "REJECTED", scope.personId(), note);
    }

    /** Decide sin comprobar permisos (traslado forzado por el administrador, ya validado por el módulo). */
    @Transactional
    public void decideInternal(UUID id, boolean approve, UUID byPerson, String note) {
        ApprovalRow row = lockAny(id);
        if (!"PENDING".equals(row.status())) {
            throw new Exceptions("error.common.alreadyDecided", HttpStatus.CONFLICT);
        }
        apply(handler(row.type()), row, approve ? "APPROVED" : "REJECTED", byPerson, note);
    }

    /** Aplica el nuevo estado de forma atómica (update condicionado a PENDING) y avisa al solicitante. */
    private void apply(ApprovalHandler h, ApprovalRow row, String status, UUID by, String note) {
        int n = jdbc.update("""
                update approval_request set status = :s, decided_by = :by, decided_at = :now, decision_note = :note, waiting_info = false,
                    updated_at = :now, updated_by = :by, version = version + 1 where id = :id and status = 'PENDING'
                """, new MapSqlParameterSource("s", status).addValue("by", by).addValue("now", Timestamp.from(clock.instant()))
                .addValue("note", cut(note, 500)).addValue("id", row.id()));
        if (n == 0) {
            throw new Exceptions("error.common.alreadyDecided", HttpStatus.CONFLICT);
        }
        Decision d = new Decision(row, by, note);
        switch (status) {
            case "APPROVED" -> h.onApprove(d);
            case "REJECTED" -> h.onReject(d);
            case "CANCELLED" -> h.onCancel(d);
            default -> h.onExpire(d);
        }
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("type", row.type());
        diff.put("status", status);
        if (note != null) {
            diff.put("note", note);
        }
        audit.record(new AuditService.Command(h.moduleCode(), switch (status) {
            case "APPROVED" -> "APPROVE";
            case "REJECTED" -> "REJECT";
            case "CANCELLED" -> "CANCEL";
            default -> "EXPIRE";
        }, "ApprovalRequest", row.id(), row.organizationId(), row.branchId(), diff));
        String suffix = switch (status) {
            case "APPROVED" -> "APPROVED";
            case "REJECTED" -> "REJECTED";
            case "EXPIRED" -> "EXPIRED";
            default -> null;
        };
        if (suffix != null) {
            Map<String, String> extra = new HashMap<>();
            if (note != null) {
                extra.put("note", note);
            }
            notify(h, suffix, row, List.of(row.requestedBy()), extra);
        }
    }

    @Transactional
    public ApprovalDtos.Summary cancel(AuthenticatedActor actor, AccessScope scope, UUID id) {
        ApprovalRow row = lock(scope, id);
        if (!row.requestedBy().equals(scope.personId())) {
            throw new Exceptions("error.approval.cannotCancel", HttpStatus.FORBIDDEN);                            // [V5]
        }
        if (!"PENDING".equals(row.status())) {
            throw new Exceptions("error.approval.cannotCancel", HttpStatus.CONFLICT);
        }
        apply(handler(row.type()), row, "CANCELLED", scope.personId(), null);
        return loadSummary(actor, scope, id);
    }

    // ---------------------------------------------------------------- comentarios y "pedir datos"
    @Transactional
    public ApprovalDtos.Detail comment(AuthenticatedActor actor, AccessScope scope, UUID id, ApprovalDtos.CommentRequest r) {
        String body = r == null ? null : trim(r.body());
        if (body == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "comentario");
        }
        if (body.length() > MAX_TEXT) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "comentario");
        }
        ApprovalRow row = lock(scope, id);
        if (!"PENDING".equals(row.status())) {
            throw new Exceptions("error.common.alreadyDecided", HttpStatus.CONFLICT);
        }
        ApprovalHandler h = handler(row.type());
        boolean requester = row.requestedBy().equals(scope.personId());
        boolean decider = !requester && canDecide(actor, scope, row, h);
        if (!requester && !decider) {
            throw new Exceptions("error.approval.notAllowed", HttpStatus.FORBIDDEN);
        }
        boolean info = decider && r.requestInfo() != null && r.requestInfo();
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("insert into approval_comment (id, request_id, author_id, kind, body, created_at) values (:id, :r, :a, :k, :b, :now)",
                new MapSqlParameterSource("id", UUID.randomUUID()).addValue("r", id).addValue("a", scope.personId())
                        .addValue("k", info ? "INFO_REQUEST" : "COMMENT").addValue("b", body).addValue("now", now));
        jdbc.update("update approval_request set waiting_info = :w, updated_at = :now, updated_by = :by where id = :id",
                new MapSqlParameterSource("w", info).addValue("now", now).addValue("by", scope.personId()).addValue("id", id));
        Map<String, String> params = new HashMap<>(h.notifyParams(row));
        params.put("by", personName(scope.personId()));
        params.put("comment", body.length() > 120 ? body.substring(0, 117) + "…" : body);
        String link = "/app/approvals?id=" + id;
        if (decider) {
            notifications.toPersons(info ? NotificationType.APPROVAL_INFO : NotificationType.APPROVAL_COMMENT, row.organizationId(), List.of(row.requestedBy()), params, link, null);
        } else {
            List<UUID> to = new ArrayList<>(notifications.branchAdmins(row.organizationId(), row.branchId()));
            to.remove(scope.personId());
            notifications.toPersons(NotificationType.APPROVAL_COMMENT, row.organizationId(), to, params, link, null);
        }
        audit.record(new AuditService.Command(h.moduleCode(), info ? "INFO_REQUEST" : "COMMENT", "ApprovalRequest", id, row.organizationId(), row.branchId(),
                Map.of("type", row.type())));
        return get(actor, scope, id);
    }

    // ---------------------------------------------------------------- lote
    /** Aprueba o rechaza hasta 50 solicitudes del mismo tipo; cada una en su propia transacción (un fallo no arrastra a las demás). */
    public ApprovalDtos.BulkResult bulk(AuthenticatedActor actor, AccessScope scope, ApprovalDtos.BulkRequest r) {
        List<UUID> ids = r == null || r.ids() == null ? List.of() : r.ids().stream().distinct().toList();
        if (ids.isEmpty()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "solicitudes");
        }
        if (ids.size() > BULK_MAX) {
            throw new Exceptions("error.approval.bulkLimit", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String action = r.action() == null ? "" : r.action().trim().toUpperCase();
        if (!action.equals("APPROVE") && !action.equals("REJECT")) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "acción");
        }
        if (action.equals("REJECT") && trim(r.reason()) == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "motivo");
        }
        List<String> types = jdbc.queryForList("select distinct type from approval_request where organization_id = :o and id in (:ids)",
                new MapSqlParameterSource("o", scope.organizationId()).addValue("ids", ids), String.class);
        if (types.size() > 1) {
            throw new Exceptions("error.approval.bulkLimit", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        TransactionTemplate tx = new TransactionTemplate(txm);
        List<ApprovalDtos.BulkItem> items = new ArrayList<>();
        int ok = 0;
        for (UUID id : ids) {
            try {
                tx.executeWithoutResult(s -> decide(actor, scope, id, action.equals("APPROVE"), r.reason()));
                items.add(new ApprovalDtos.BulkItem(id, true, null));
                ok++;
            } catch (Exceptions e) {
                items.add(new ApprovalDtos.BulkItem(id, false, e.getMessage()));
            }
        }
        return new ApprovalDtos.BulkResult(ok, items.size() - ok, items);
    }

    // ---------------------------------------------------------------- mantenimiento (job)
    /** Marca EXPIRED las pendientes vencidas de los tipos con vigencia [V6]. */
    public int expirePending() {
        List<UUID> due = jdbc.queryForList("select id from approval_request where status = 'PENDING' and expires_at is not null and expires_at <= :now",
                new MapSqlParameterSource("now", Timestamp.from(clock.instant())), UUID.class);
        TransactionTemplate tx = new TransactionTemplate(txm);
        int n = 0;
        for (UUID id : due) {
            try {
                tx.executeWithoutResult(s -> {
                    ApprovalRow row = lockAny(id);
                    if ("PENDING".equals(row.status())) {
                        apply(handler(row.type()), row, "EXPIRED", null, null);
                    }
                });
                n++;
            } catch (RuntimeException e) {
                log.warn("[APPROVAL] no se pudo vencer {}: {}", id, e.getMessage());
            }
        }
        return n;
    }

    /** Recordatorio único a quienes deciden cuando una solicitud pasa de {@code days} días sin resolverse. */
    public int remindStale(int days) {
        List<Object[]> due = jdbc.query("select id, organization_id, branch_id from approval_request where status = 'PENDING' and reminded_at is null and created_at <= :cut",
                new MapSqlParameterSource("cut", Timestamp.from(clock.instant().minus(Duration.ofDays(days)))),
                (rs, i) -> new Object[]{rs.getObject(1), rs.getObject(2), rs.getObject(3)});
        TransactionTemplate tx = new TransactionTemplate(txm);
        int n = 0;
        for (Object[] d : due) {
            UUID id = (UUID) d[0];
            UUID org = (UUID) d[1];
            UUID branch = (UUID) d[2];
            tx.executeWithoutResult(s -> {
                int u = jdbc.update("update approval_request set reminded_at = :now where id = :id and reminded_at is null and status = 'PENDING'",
                        new MapSqlParameterSource("now", Timestamp.from(clock.instant())).addValue("id", id));
                if (u > 0) {
                    ApprovalRow row = lockAny(id);
                    Map<String, String> params = new HashMap<>(handler(row.type()).notifyParams(row));
                    params.put("days", String.valueOf(days));
                    notifications.toPersons(NotificationType.APPROVAL_REMINDER, org, notifications.branchAdmins(org, branch), params,
                            "/app/approvals?id=" + id, "approval-reminder:" + id);
                }
            });
            n++;
        }
        return n;
    }

    // ---------------------------------------------------------------- helpers
    private static final String SELECT = """
            select r.id, r.type, r.status, r.branch_id, b.name as branch_name, r.related_branch_id, rb.name as related_name, r.subject_type, r.subject_id,
                   sp.first_name as sfn, sp.last_name as sln, r.requested_by, rp.first_name as rfn, rp.last_name as rln, r.reason, r.waiting_info,
                   r.created_at, r.decided_at, dp.first_name as dfn, dp.last_name as dln, r.decision_note, r.expires_at, cast(r.payload as text) as payload
            """;
    private static final String FROM = """
            from approval_request r join branch b on b.id = r.branch_id left join branch rb on rb.id = r.related_branch_id
              join person rp on rp.id = r.requested_by
              left join person sp on sp.id = r.subject_id and r.subject_type = 'PERSON'
              left join person dp on dp.id = r.decided_by
            """;

    private String visible(AccessScope scope, MapSqlParameterSource ps) {
        if (scope.allBranches()) {
            return "true";
        }
        ps.addValue("sb", branchList(scope));
        return "(r.branch_id in (:sb) or r.related_branch_id in (:sb) or r.requested_by = :me)";
    }

    private static List<UUID> branchList(AccessScope scope) {
        return scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
    }

    /** Tipos de solicitud que el actor puede decidir (tiene A en el módulo dueño). */
    private Set<String> decidableTypes(AuthenticatedActor actor) {
        return handlers().values().stream().filter(h -> authz.effectiveActions(actor, h.moduleCode()).contains(Action.A.name()))
                .map(ApprovalHandler::type).collect(Collectors.toSet());
    }

    private boolean canDecide(AuthenticatedActor actor, AccessScope scope, ApprovalRow row, ApprovalHandler h) {
        return "PENDING".equals(row.status()) && !row.requestedBy().equals(scope.personId()) && scope.canSeeBranch(row.branchId())
                && authz.effectiveActions(actor, h.moduleCode()).contains(Action.A.name());
    }

    private ApprovalDtos.Summary loadSummary(AuthenticatedActor actor, AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId()).addValue("me", scope.personId()).addValue("id", id);
        String vis = visible(scope, ps);
        Set<String> decidable = decidableTypes(actor);
        return jdbc.query(SELECT + FROM + " where r.organization_id = :org and r.id = :id and " + vis, ps, (rs, i) -> summary(rs, scope, decidable)).stream()
                .findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private ApprovalDtos.Summary summary(java.sql.ResultSet rs, AccessScope scope, Set<String> decidable) throws java.sql.SQLException {
        String type = rs.getString("type");
        String status = rs.getString("status");
        UUID branchId = (UUID) rs.getObject("branch_id");
        UUID by = (UUID) rs.getObject("requested_by");
        boolean mine = by.equals(scope.personId());
        boolean reveal = handlers().containsKey(type) ? handlers().get(type).revealSubject() || scope.canSeeBranch(branchId) : scope.canSeeBranch(branchId);
        String subject = rs.getString("sfn") == null || !reveal ? null : rs.getString("sfn") + " " + rs.getString("sln");
        Instant created = rs.getTimestamp("created_at").toInstant();
        Instant decidedAt = rs.getTimestamp("decided_at") == null ? null : rs.getTimestamp("decided_at").toInstant();
        boolean can = "PENDING".equals(status) && !mine && decidable.contains(type) && scope.canSeeBranch(branchId);
        return new ApprovalDtos.Summary((UUID) rs.getObject("id"), type, status, branchId, rs.getString("branch_name"), (UUID) rs.getObject("related_branch_id"),
                rs.getString("related_name"), (UUID) rs.getObject("subject_id"), subject, by, rs.getString("rfn") + " " + rs.getString("rln"),
                rs.getString("reason"), rs.getBoolean("waiting_info"), created, Duration.between(created, decidedAt == null ? clock.instant() : decidedAt).toDays(),
                decidedAt, rs.getString("dfn") == null ? null : rs.getString("dfn") + " " + rs.getString("dln"), rs.getString("decision_note"),
                rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(), can, mine, payload(rs.getString("payload")));
    }

    /** Bloquea la solicitud si está en el alcance (404 si no). */
    private ApprovalRow lock(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId()).addValue("me", scope.personId()).addValue("id", id);
        String vis = visible(scope, ps);
        List<ApprovalRow> rows = jdbc.query(ROW + " where r.organization_id = :org and r.id = :id and " + vis + " for update", ps, (rs, i) -> row(rs));
        if (rows.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return rows.get(0);
    }

    private ApprovalRow lockAny(UUID id) {
        List<ApprovalRow> rows = jdbc.query(ROW + " where r.id = :id for update", new MapSqlParameterSource("id", id), (rs, i) -> row(rs));
        if (rows.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return rows.get(0);
    }

    private static final String ROW = "select r.id, r.organization_id, r.branch_id, r.related_branch_id, r.type, r.subject_type, r.subject_id, r.requested_by, r.status,"
            + " r.reason, cast(r.payload as text) as payload from approval_request r";

    private ApprovalRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ApprovalRow((UUID) rs.getObject("id"), (UUID) rs.getObject("organization_id"), (UUID) rs.getObject("branch_id"),
                (UUID) rs.getObject("related_branch_id"), rs.getString("type"), rs.getString("subject_type"), (UUID) rs.getObject("subject_id"),
                (UUID) rs.getObject("requested_by"), rs.getString("status"), rs.getString("reason"), payload(rs.getString("payload")));
    }

    private void notify(ApprovalHandler h, String suffix, ApprovalRow row, Collection<UUID> to) {
        notify(h, suffix, row, to, Map.of());
    }

    private void notify(ApprovalHandler h, String suffix, ApprovalRow row, Collection<UUID> to, Map<String, String> extra) {
        NotificationType type;
        try {
            type = NotificationType.valueOf(h.notificationPrefix() + "_" + suffix);
        } catch (IllegalArgumentException e) {
            log.warn("[APPROVAL] sin tipo de aviso {}_{}", h.notificationPrefix(), suffix);
            return;
        }
        Map<String, String> params = new HashMap<>(h.notifyParams(row));
        params.putAll(extra);
        notifications.toPersons(type, row.organizationId(), to, params, "/app/approvals?id=" + row.id(), null);
    }

    private String personName(UUID personId) {
        List<String> n = jdbc.queryForList("select first_name || ' ' || last_name from person where id = :id", new MapSqlParameterSource("id", personId), String.class);
        return n.isEmpty() ? "" : n.get(0);
    }

    private String json(Map<String, Object> m) {
        try {
            return mapper.writeValueAsString(m == null ? Map.of() : m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> payload(String s) {
        try {
            return s == null ? Map.of() : mapper.readValue(s, PAYLOAD);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String cut(String s, int max) {
        String t = trim(s);
        return t == null ? null : t.length() > max ? t.substring(0, max) : t;
    }
}
