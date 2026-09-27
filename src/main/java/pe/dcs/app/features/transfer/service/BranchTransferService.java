package pe.dcs.app.features.transfer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.dcs.app.features.approval.service.ApprovalEngine;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
import pe.dcs.app.features.person.service.PersonLookupService;
import pe.dcs.app.features.person.service.PersonService;
import pe.dcs.app.features.transfer.dto.TransferDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.vo.DocumentValidator;
import pe.dcs.app.shared.vo.DocumentType;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M21 · Traslado de personas entre sedes. La solicitud vive en el motor de aprobaciones (decide la sede DESTINO); aprobada, se ejecuta
 * de forma atómica: se cierra el periodo de sede actual, se abre el del destino y cada módulo registrado como
 * {@link BranchTransferParticipant} mueve lo suyo. Historial y finanzas se conservan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BranchTransferService {

    public static final String MODULE = "BRANCH_TRANSFER";
    private static final String ENTITY = "BranchTransfer";
    private static final int MAX_DAYS_AHEAD = 60;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final AuditService audit;
    private final AuthorizationService authz;
    private final ApprovalEngine engine;
    private final PersonService persons;
    private final PersonLookupService lookup;
    private final NotificationService notifications;
    private final ObjectProvider<BranchTransferParticipant> participantProvider;
    private final PlatformTransactionManager txm;

    // ---------------------------------------------------------------- consulta
    @Transactional(readOnly = true)
    public PageResponse<TransferDtos.Summary> search(AuthenticatedActor actor, AccessScope scope, TransferDtos.Search req) {
        TransferDtos.Search.Filters f = req == null || req.filters() == null ? new TransferDtos.Search.Filters(null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId());
        StringBuilder w = new StringBuilder("t.organization_id = :org and ").append(visible(scope, ps));
        if (f.status() != null && !f.status().isBlank()) {
            String st = f.status().trim().toUpperCase();
            if (!java.util.Set.of("PENDING", "APPROVED", "EXECUTED", "REJECTED", "CANCELLED", "EXPIRED").contains(st)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
            }
            w.append(" and t.status = :st");
            ps.addValue("st", st);
        }
        if (f.branchId() != null) {
            w.append(" and (t.from_branch_id = :fb or t.to_branch_id = :fb)");
            ps.addValue("fb", f.branchId());
        }
        if (f.q() != null && !f.q().isBlank()) {
            ps.addValue("q", "%" + f.q().trim().toLowerCase().replace("%", "").replace("_", "") + "%");
            w.append(" and (lower(p.first_name || ' ' || p.last_name) like :q or lower(coalesce(p.doc_number, '')) like :q)");
        }
        String from = FROM + " where " + w;
        Long total = jdbc.queryForObject("select count(*) " + from, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 100));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        boolean canA = authz.effectiveActions(actor, MODULE).contains(Action.A.name());
        List<TransferDtos.Summary> rows = jdbc.query(SELECT + from + " order by t.created_at desc limit :lim offset :off", ps, (rs, i) -> summary(rs, scope, canA));
        long tt = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) tt, (int) Math.ceil(tt / (double) size), size, page));
    }

    @Transactional(readOnly = true)
    public TransferDtos.Summary get(AuthenticatedActor actor, AccessScope scope, UUID id) {
        return loadSummary(actor, scope, id);
    }

    /** Qué se cerrará o moverá al ejecutar. Sirve para decidir (PENDING) y para confirmar la ejecución (APPROVED). */
    @Transactional(readOnly = true)
    public TransferDtos.Preview preview(AuthenticatedActor actor, AccessScope scope, UUID id) {
        TransferDtos.Summary s = loadSummary(actor, scope, id);
        List<TransferDtos.Impact> impacts = new ArrayList<>();
        impacts.add(new TransferDtos.Impact("branchPeriod", 1));
        if (s.status().equals("PENDING") || s.status().equals("APPROVED")) {
            for (BranchTransferParticipant p : participants()) {
                impacts.add(new TransferDtos.Impact(p.key(), p.count(scope.organizationId(), s.personId(), s.fromBranchId())));
            }
        }
        return new TransferDtos.Preview(s, impacts, !s.effectiveDate().isAfter(LocalDate.now(clock)));
    }

    /** Sedes activas de la organización: el destino puede estar fuera del alcance de quien solicita. */
    @Transactional(readOnly = true)
    public List<TransferDtos.Destination> destinations(AccessScope scope) {
        return jdbc.query("select id, name, code from branch where organization_id = :o and status = 'ACTIVE' order by is_main desc, lower(name)",
                new MapSqlParameterSource("o", scope.organizationId()), (rs, i) -> new TransferDtos.Destination((UUID) rs.getObject(1), rs.getString(2), rs.getString(3)));
    }

    // ---------------------------------------------------------------- solicitud
    @Transactional
    public TransferDtos.Summary create(AuthenticatedActor actor, AccessScope scope, TransferDtos.CreateRequest r) {
        UUID id = open(actor, scope, r, false);
        return loadSummary(actor, scope, id);
    }

    /** El administrador de la organización traslada sin esperar la aprobación de la sede destino; queda auditado con el motivo. */
    @Transactional
    public TransferDtos.Summary force(AuthenticatedActor actor, AccessScope scope, TransferDtos.CreateRequest r) {
        if (scope.role() != RoleType.ORG_ADMIN) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        if (r == null || r.reason() == null || r.reason().trim().length() < 5) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "motivo");
        }
        UUID id = open(actor, scope, r, true);
        Row t = lockRow(id, scope.organizationId());
        engine.decideInternal(t.requestId(), true, scope.personId(), "Traslado forzado: " + r.reason().trim());
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("reason", r.reason().trim());
        audit.record(new AuditService.Command(MODULE, "FORCE", ENTITY, id, scope.organizationId(), t.toBranch(), d));
        Map<String, String> params = params(t);
        List<UUID> to = new ArrayList<>(notifications.branchAdmins(scope.organizationId(), t.toBranch()));
        to.remove(scope.personId());
        notifications.toPersons(NotificationType.TRANSFER_APPROVED, scope.organizationId(), to, params, "/app/transfers?id=" + id, null);
        if (!t.effectiveDate().isAfter(LocalDate.now(clock))) {
            run(lockRow(id, scope.organizationId()), scope.personId());
        }
        return loadSummary(actor, scope, id);
    }

    private UUID open(AuthenticatedActor actor, AccessScope scope, TransferDtos.CreateRequest r, boolean forced) {
        if (r == null || r.toBranchId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sede destino");
        }
        PersonRef p = resolvePerson(scope, r);
        BranchRef to = branch(scope.organizationId(), r.toBranchId());
        if (to == null) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        UUID from = p.branchId();
        if (from == null || !p.active()) {
            throw new Exceptions("error.transfer.personInactive", HttpStatus.UNPROCESSABLE_ENTITY);                // [V4]
        }
        if (!scope.canSeeBranch(from) && !scope.canSeeBranch(to.id())) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        if (from.equals(to.id()) || !"ACTIVE".equals(to.status())) {
            throw new Exceptions("error.transfer.sameBranch", HttpStatus.UNPROCESSABLE_ENTITY);                    // [V5]
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate date = r.effectiveDate() == null ? today : r.effectiveDate();
        if (date.isBefore(today) || date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw new Exceptions("error.transfer.dateRange", HttpStatus.UNPROCESSABLE_ENTITY);                     // [V7]
        }
        Boolean open = jdbc.queryForObject("select exists (select 1 from branch_transfer where person_id = :p and status in ('PENDING','APPROVED'))",
                new MapSqlParameterSource("p", p.id()), Boolean.class);
        if (Boolean.TRUE.equals(open)) {
            throw new Exceptions("error.transfer.openExists", HttpStatus.CONFLICT);                                // [V6]
        }
        String reason = r.reason() == null || r.reason().isBlank() ? null : r.reason().trim();
        if (reason != null && reason.length() > 300) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "motivo");
        }
        TransferDtos.Options opt = normalize(r.options());
        BranchRef fromRef = branch(scope.organizationId(), from);
        UUID transferId = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transferId", transferId.toString());
        payload.put("fromBranchName", fromRef == null ? null : fromRef.name());
        payload.put("toBranchName", to.name());
        payload.put("effectiveDate", date.toString());
        UUID requestId = engine.open(new ApprovalEngine.NewRequest(scope.organizationId(), to.id(), from, "BRANCH_TRANSFER", "PERSON", p.id(),
                scope.personId(), reason, payload));
        try {
            jdbc.update("""
                    insert into branch_transfer (id, organization_id, request_id, person_id, from_branch_id, to_branch_id, reason, effective_date, options,
                        status, forced, created_at, created_by, version)
                    values (:id, :org, :req, :p, :f, :t, :reason, :d, cast(:opt as jsonb), 'PENDING', :forced, :now, :by, 0)
                    """, new MapSqlParameterSource("id", transferId).addValue("org", scope.organizationId()).addValue("req", requestId)
                    .addValue("p", p.id()).addValue("f", from).addValue("t", to.id()).addValue("reason", reason).addValue("d", Date.valueOf(date))
                    .addValue("opt", json(opt)).addValue("forced", forced).addValue("now", Timestamp.from(clock.instant())).addValue("by", scope.personId()));
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.transfer.openExists", HttpStatus.CONFLICT);
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("from", fromRef == null ? null : fromRef.name());
        d.put("to", to.name());
        d.put("effectiveDate", date.toString());
        if (reason != null) {
            d.put("reason", reason);
        }
        audit.record(new AuditService.Command(MODULE, "REQUEST", ENTITY, transferId, scope.organizationId(), to.id(), d));
        return transferId;
    }

    @Transactional
    public TransferDtos.Summary cancel(AuthenticatedActor actor, AccessScope scope, UUID id) {
        Row t = lockRow(id, scope.organizationId());
        checkScope(scope, t);
        engine.cancel(actor, scope, t.requestId());
        return loadSummary(actor, scope, id);
    }

    // ---------------------------------------------------------------- ejecución
    /** Ejecuta un traslado aprobado cuya fecha efectiva ya llegó. Todo o nada [V8]. */
    @Transactional
    public TransferDtos.Summary execute(AuthenticatedActor actor, AccessScope scope, UUID id) {
        Row t = lockRow(id, scope.organizationId());
        checkScope(scope, t);
        if (!"APPROVED".equals(t.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, t.status());
        }
        if (t.effectiveDate().isAfter(LocalDate.now(clock))) {
            throw new Exceptions("error.transfer.notYet", HttpStatus.UNPROCESSABLE_ENTITY, t.effectiveDate());
        }
        run(t, scope.personId());
        return loadSummary(actor, scope, id);
    }

    /** Job: ejecuta los aprobados con fecha efectiva vencida (cada uno en su transacción; si uno falla queda APPROVED y se reintenta). */
    public int executeDue() {
        List<UUID> due = jdbc.queryForList("select id from branch_transfer where status = 'APPROVED' and effective_date <= :today",
                new MapSqlParameterSource("today", Date.valueOf(LocalDate.now(clock))), UUID.class);
        TransactionTemplate tx = new TransactionTemplate(txm);
        int n = 0;
        for (UUID id : due) {
            try {
                tx.executeWithoutResult(s -> {
                    Row t = lockRow(id, null);
                    if ("APPROVED".equals(t.status())) {
                        UUID by = jdbc.queryForObject("select coalesce(decided_by, requested_by) from approval_request where id = :r",
                                new MapSqlParameterSource("r", t.requestId()), UUID.class);
                        run(t, by);
                    }
                });
                n++;
            } catch (RuntimeException e) {
                log.warn("[TRANSFER] no se pudo ejecutar {}: {}", id, e.getMessage());
            }
        }
        return n;
    }

    /** Cuerpo del traslado; se ejecuta dentro de la transacción del llamador. */
    private void run(Row t, UUID by) {
        try {
            PersonRef p = person(t.orgId(), t.personId());
            if (p == null || !p.active() || !t.fromBranch().equals(p.branchId())) {
                throw new Exceptions("error.transfer.personInactive", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            BranchRef to = branch(t.orgId(), t.toBranch());
            if (to == null || !"ACTIVE".equals(to.status())) {
                throw new Exceptions("error.transfer.sameBranch", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            persons.applyTransfer(by, t.orgId(), t.personId(), t.toBranch(), t.reason());
            TransferDtos.Options o = t.options();
            BranchTransferParticipant.Context ctx = new BranchTransferParticipant.Context(t.orgId(), t.personId(), t.fromBranch(), t.toBranch(), by,
                    Boolean.TRUE.equals(o.moveMembership()), Boolean.TRUE.equals(o.endGroups()), Boolean.TRUE.equals(o.endMinistries()));
            for (BranchTransferParticipant part : participants()) {
                part.execute(ctx);
            }
            jdbc.update("update branch_transfer set status = 'EXECUTED', executed_at = :now, executed_by = :by, updated_at = :now, updated_by = :by, version = version + 1 where id = :id",
                    new MapSqlParameterSource("now", Timestamp.from(clock.instant())).addValue("by", by).addValue("id", t.id()));
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("person", t.personId().toString());
            d.put("from", t.fromBranch().toString());
            d.put("to", t.toBranch().toString());
            audit.record(new AuditService.Command(MODULE, "EXECUTE", ENTITY, t.id(), t.orgId(), t.toBranch(), d));
            List<UUID> to2 = new ArrayList<>();
            to2.add(t.requestedBy());
            to2.addAll(notifications.branchAdmins(t.orgId(), t.fromBranch()));
            to2.addAll(notifications.branchAdmins(t.orgId(), t.toBranch()));
            notifications.toPersons(NotificationType.TRANSFER_EXECUTED, t.orgId(), to2, params(t), "/app/transfers?id=" + t.id(), "transfer-exec:" + t.id());
        } catch (Exceptions e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("[TRANSFER] falló la ejecución de {}", t.id(), e);
            throw new Exceptions("error.transfer.executionFailed", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    // ---------------------------------------------------------------- lo que llama el handler del motor
    /** Refleja la decisión del motor en el traslado. */
    @Transactional
    public void mark(UUID requestId, String status) {
        jdbc.update("update branch_transfer set status = :s, updated_at = :now, version = version + 1 where request_id = :r",
                new MapSqlParameterSource("s", status).addValue("now", Timestamp.from(clock.instant())).addValue("r", requestId));
    }

    /** Parámetros de los avisos: persona y sedes. */
    public Map<String, String> params(UUID requestId) {
        List<Map<String, String>> r = jdbc.query("""
                select p.first_name || ' ' || p.last_name as person, fb.name as f, tb.name as t, cast(bt.effective_date as text) as d
                from branch_transfer bt join person p on p.id = bt.person_id join branch fb on fb.id = bt.from_branch_id join branch tb on tb.id = bt.to_branch_id
                where bt.request_id = :r""", new MapSqlParameterSource("r", requestId), (rs, i) -> {
            Map<String, String> m = new HashMap<>();
            m.put("person", rs.getString("person"));
            m.put("from", rs.getString("f"));
            m.put("to", rs.getString("t"));
            m.put("date", rs.getString("d"));
            return m;
        });
        return r.isEmpty() ? Map.of() : r.get(0);
    }

    public UUID fromBranchOf(UUID requestId) {
        List<UUID> l = jdbc.queryForList("select from_branch_id from branch_transfer where request_id = :r", new MapSqlParameterSource("r", requestId), UUID.class);
        return l.isEmpty() ? null : l.get(0);
    }

    // ---------------------------------------------------------------- helpers
    private Map<String, String> params(Row t) {
        return params(t.requestId());
    }

    private List<BranchTransferParticipant> participants() {
        return participantProvider.orderedStream().toList();
    }

    private void checkScope(AccessScope scope, Row t) {
        if (!scope.canSeeBranch(t.fromBranch()) && !scope.canSeeBranch(t.toBranch())) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
    }

    private static TransferDtos.Options normalize(TransferDtos.Options o) {
        return new TransferDtos.Options(o == null || o.moveMembership() == null || o.moveMembership(), o == null || o.endGroups() == null || o.endGroups(),
                o == null || o.endMinistries() == null || o.endMinistries());
    }

    private record PersonRef(UUID id, UUID branchId, boolean active) {
    }

    private record BranchRef(UUID id, String name, String status) {
    }

    private record Row(UUID id, UUID orgId, UUID requestId, UUID personId, UUID fromBranch, UUID toBranch, String reason, LocalDate effectiveDate,
                       TransferDtos.Options options, String status, UUID requestedBy) {
    }

    private PersonRef resolvePerson(AccessScope scope, TransferDtos.CreateRequest r) {
        UUID id = r.personId();
        if (id == null) {
            String doc = r.docNumber() == null ? null : DocumentValidator.normalize(r.docNumber());
            if (r.docType() == null || doc == null || doc.isBlank()) {
                throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "persona");
            }
            id = lookup.find(scope.organizationId(), r.docType(), doc).map(PersonLookupService.PersonMin::id)
                    .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        }
        PersonRef p = person(scope.organizationId(), id);
        if (p == null) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return p;
    }

    private PersonRef person(UUID orgId, UUID id) {
        return jdbc.query("select id, primary_branch_id, status, anonymized_at from person where id = :id and organization_id = :o",
                new MapSqlParameterSource("id", id).addValue("o", orgId),
                (rs, i) -> new PersonRef((UUID) rs.getObject(1), (UUID) rs.getObject(2), "ACTIVE".equals(rs.getString(3)) && rs.getTimestamp(4) == null)).stream().findFirst().orElse(null);
    }

    private BranchRef branch(UUID orgId, UUID id) {
        return jdbc.query("select id, name, status from branch where id = :id and organization_id = :o", new MapSqlParameterSource("id", id).addValue("o", orgId),
                (rs, i) -> new BranchRef((UUID) rs.getObject(1), rs.getString(2), rs.getString(3))).stream().findFirst().orElse(null);
    }

    private Row lockRow(UUID id, UUID orgId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String org = "";
        if (orgId != null) {
            org = " and t.organization_id = :o";
            ps.addValue("o", orgId);
        }
        List<Row> rows = jdbc.query("""
                select t.id, t.organization_id, t.request_id, t.person_id, t.from_branch_id, t.to_branch_id, t.reason, t.effective_date, cast(t.options as text) as opt,
                       t.status, r.requested_by
                from branch_transfer t join approval_request r on r.id = t.request_id where t.id = :id""" + org + " for update of t", ps,
                (rs, i) -> new Row((UUID) rs.getObject(1), (UUID) rs.getObject(2), (UUID) rs.getObject(3), (UUID) rs.getObject(4), (UUID) rs.getObject(5),
                        (UUID) rs.getObject(6), rs.getString(7), rs.getDate(8).toLocalDate(), options(rs.getString("opt")), rs.getString("status"), (UUID) rs.getObject(11)));
        if (rows.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return rows.get(0);
    }

    private static final String SELECT = """
            select t.id, t.request_id, t.status, t.person_id, p.first_name as pfn, p.last_name as pln, t.from_branch_id, fb.name as fname, t.to_branch_id, tb.name as tname,
                   t.effective_date, t.reason, cast(t.options as text) as opt, t.forced, r.requested_by, rp.first_name as rfn, rp.last_name as rln,
                   t.created_at, t.executed_at
            """;
    private static final String FROM = """
            from branch_transfer t join person p on p.id = t.person_id join branch fb on fb.id = t.from_branch_id join branch tb on tb.id = t.to_branch_id
              join approval_request r on r.id = t.request_id join person rp on rp.id = r.requested_by
            """;

    private String visible(AccessScope scope, MapSqlParameterSource ps) {
        if (scope.allBranches()) {
            return "true";
        }
        ps.addValue("sb", scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds()));
        return "(t.from_branch_id in (:sb) or t.to_branch_id in (:sb))";
    }

    private TransferDtos.Summary loadSummary(AuthenticatedActor actor, AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId()).addValue("id", id);
        String vis = visible(scope, ps);
        boolean canA = authz.effectiveActions(actor, MODULE).contains(Action.A.name());
        return jdbc.query(SELECT + FROM + " where t.organization_id = :org and t.id = :id and " + vis, ps, (rs, i) -> summary(rs, scope, canA)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private TransferDtos.Summary summary(java.sql.ResultSet rs, AccessScope scope, boolean canA) throws java.sql.SQLException {
        String status = rs.getString("status");
        LocalDate date = rs.getDate("effective_date").toLocalDate();
        UUID from = (UUID) rs.getObject("from_branch_id");
        UUID to = (UUID) rs.getObject("to_branch_id");
        UUID by = (UUID) rs.getObject("requested_by");
        boolean canExec = "APPROVED".equals(status) && canA && !date.isAfter(LocalDate.now(clock)) && (scope.canSeeBranch(from) || scope.canSeeBranch(to));
        return new TransferDtos.Summary((UUID) rs.getObject("id"), (UUID) rs.getObject("request_id"), status, (UUID) rs.getObject("person_id"),
                rs.getString("pfn") + " " + rs.getString("pln"), from, rs.getString("fname"), to, rs.getString("tname"), date, rs.getString("reason"),
                options(rs.getString("opt")), rs.getBoolean("forced"), by, rs.getString("rfn") + " " + rs.getString("rln"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("executed_at") == null ? null : rs.getTimestamp("executed_at").toInstant(), canExec,
                "PENDING".equals(status) && by.equals(scope.personId()));
    }

    private TransferDtos.Options options(String s) {
        try {
            return normalize(s == null ? null : mapper.readValue(s, TransferDtos.Options.class));
        } catch (JsonProcessingException e) {
            return normalize(null);
        }
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

}
