package pe.dcs.app.features.rite.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.approval.service.ApprovalEngine;
import pe.dcs.app.features.approval.service.ApprovalHandler;
import pe.dcs.app.features.auth.service.SecretCipher;
import pe.dcs.app.features.rite.dto.RiteDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M08 · Membresía. Una persona tiene a lo sumo una membresía vigente [V3]; ingresar pasa por aprobación (M21) y requisitos [V10], salvo el registro
 * directo (acción A). Terminar exige motivo y fecha [V4]; las notas de una salida disciplinaria se guardan cifradas y solo las lee quien tiene H.
 */
@Service
@RequiredArgsConstructor
public class MembershipService {

    static final String MODULE = "MEMBERSHIP";
    static final String TYPE = "MEMBERSHIP";
    private static final String ENTITY = "Membership";
    private static final Set<String> KINDS = Set.of("MEMBER", "ATTENDEE");
    private static final Set<String> STATUSES = Set.of("PENDING", "ACTIVE", "ENDED", "CANCELLED");
    private static final Set<String> EXITS = Set.of("WITHDRAWN", "TRANSFERRED", "DISCIPLINARY");

    private final NamedParameterJdbcTemplate jdbc;
    private final RiteSupport support;
    private final RiteRequirementService requirements;
    private final AuthorizationService authz;
    private final ApprovalEngine engine;
    private final SecretCipher cipher;
    private final AuditService audit;
    private final Clock clock;

    private static final String SELECT = "select m.*, trim(p.first_name || ' ' || p.last_name) as person_name, p.birth_date as birth_date, b.name as branch_name,"
            + " trim(rq.first_name || ' ' || rq.last_name) as req_name from membership m join person p on p.id = m.person_id join branch b on b.id = m.branch_id"
            + " left join person rq on rq.id = m.requested_by";

    /** Fila cargada. */
    record Row(UUID id, UUID orgId, UUID branchId, UUID personId, String kind, String status, boolean current, LocalDate startDate, String overrideReason, UUID approvalId, long version,
               LocalDate birthDate) {
    }

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public PageResponse<RiteDtos.MembershipResponse> search(AuthenticatedActor actor, AccessScope scope, RiteDtos.MembershipSearch req) {
        RiteDtos.MembershipSearch.Filters f = req == null || req.filters() == null ? new RiteDtos.MembershipSearch.Filters(null, null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(RiteSupport.readable(MODULE, scope, ps, "m"));
        if (RiteSupport.hasText(f.q())) {
            ps.addValue("q", "%" + f.q().trim().toLowerCase().replace("%", "").replace("_", "") + "%");
            w.append(" and (lower(p.first_name || ' ' || p.last_name) like :q or lower(p.last_name || ' ' || p.first_name) like :q or lower(p.doc_number) like :q)");
        }
        if (f.branchId() != null) {
            w.append(" and m.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (RiteSupport.hasText(f.status())) {
            String st = f.status().trim().toUpperCase();
            if (!STATUSES.contains(st)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
            }
            w.append(" and m.status = :st");
            ps.addValue("st", st);
        }
        if (RiteSupport.hasText(f.kind())) {
            String k = f.kind().trim().toUpperCase();
            if (!KINDS.contains(k)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo");
            }
            w.append(" and m.kind = :kind");
            ps.addValue("kind", k);
        }
        Long total = jdbc.queryForObject("select count(*) from membership m join person p on p.id = m.person_id where " + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        boolean notes = authz.effectiveActions(actor, MODULE).contains("H");
        List<RiteDtos.MembershipResponse> rows = jdbc.query(SELECT + " where " + w + " order by (m.status = 'PENDING') desc, m.created_at desc limit :lim offset :off", ps,
                (rs, i) -> toResponse(scope, notes, rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    @Transactional(readOnly = true)
    public RiteDtos.MembershipResponse get(AuthenticatedActor actor, AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String w = RiteSupport.readable(MODULE, scope, ps, "m");
        boolean notes = authz.effectiveActions(actor, MODULE).contains("H");
        return jdbc.query(SELECT + " where m.id = :id and " + w, ps, (rs, i) -> toResponse(scope, notes, rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    /** Historial de membresías de una persona (para la línea de vida eclesial). */
    @Transactional(readOnly = true)
    public List<RiteDtos.MembershipResponse> ofPerson(AuthenticatedActor actor, AccessScope scope, UUID personId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("p", personId);
        String w = RiteSupport.readable(MODULE, scope, ps, "m");
        boolean notes = authz.effectiveActions(actor, MODULE).contains("H");
        return jdbc.query(SELECT + " where m.person_id = :p and " + w + " order by coalesce(m.start_date, m.created_at::date) desc, m.created_at desc", ps,
                (rs, i) -> toResponse(scope, notes, rs));
    }

    private RiteDtos.MembershipResponse toResponse(AccessScope scope, boolean notes, ResultSet rs) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        UUID branch = (UUID) rs.getObject("branch_id");
        String status = rs.getString("status");
        String stored = rs.getString("exit_notes");
        boolean hidden = stored != null && !notes;
        String plain = stored != null && notes ? decrypt(stored) : null;
        boolean pending = false;
        if ("PENDING".equals(status) && rs.getString("override_reason") == null) {
            java.sql.Date b = rs.getDate("birth_date");
            pending = !requirements.checklist((UUID) rs.getObject("organization_id"), TYPE, id, b == null ? List.of() : List.of(b.toLocalDate()), support.today(branch), null).complete();
        }
        return new RiteDtos.MembershipResponse(id, (UUID) rs.getObject("person_id"), rs.getString("person_name"), branch, rs.getString("branch_name"), rs.getString("kind"), status,
                rs.getBoolean("current"), date(rs, "start_date"), date(rs, "end_date"), rs.getString("exit_reason"), plain, hidden, (UUID) rs.getObject("approval_id"),
                rs.getString("origin"), rs.getString("override_reason"), rs.getString("req_name"), rs.getString("cancel_reason"), pending, !scope.canSeeBranch(branch),
                rs.getTimestamp("created_at").toInstant(), rs.getLong("version"));
    }

    private String decrypt(String stored) {
        try {
            return cipher.decrypt(stored);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static LocalDate date(ResultSet rs, String col) throws SQLException {
        java.sql.Date d = rs.getDate(col);
        return d == null ? null : d.toLocalDate();
    }

    // ---------------------------------------------------------------- alta

    @Transactional
    public RiteDtos.MembershipResponse create(AuthenticatedActor actor, AccessScope scope, RiteDtos.MembershipRequest r) {
        authz.require(actor, MODULE, Action.C);
        if (r == null || r.personId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "persona");
        }
        String kind = kind(r.kind());
        boolean record = "RECORD".equalsIgnoreCase(r.mode());
        if (r.mode() != null && !r.mode().isBlank() && !record && !"REQUEST".equalsIgnoreCase(r.mode())) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "modo");
        }
        RiteSupport.PersonInfo p = support.activeVisible(scope, r.personId());
        UUID branch = support.branchFor(scope, actor.activeBranchId(), p);
        LocalDate today = support.today(branch);
        if (r.startDate() != null && r.startDate().isAfter(today)) {
            throw new Exceptions("error.common.futureDate", HttpStatus.BAD_REQUEST);
        }
        MapSqlParameterSource q = new MapSqlParameterSource("p", p.id());
        Integer cur = jdbc.queryForObject("select count(*) from membership where person_id = :p and current", q, Integer.class);
        if (cur != null && cur > 0) {
            throw new Exceptions("error.rite.membershipCurrentExists", HttpStatus.UNPROCESSABLE_ENTITY, p.name());          // [V3]
        }
        Integer open = jdbc.queryForObject("select count(*) from membership where person_id = :p and status = 'PENDING'", q, Integer.class);
        if (open != null && open > 0) {
            throw new Exceptions("error.rite.duplicateOpen", HttpStatus.UNPROCESSABLE_ENTITY, p.name());                   // [V15]
        }
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(clock.instant());
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("b", branch).addValue("p", p.id()).addValue("k", kind)
                .addValue("sd", r.startDate() == null ? null : java.sql.Date.valueOf(r.startDate())).addValue("at", now).addValue("by", actor.ownerId()).addValue("rq", scope.personId());
        try {
            if (record) {
                authz.require(actor, MODULE, Action.A);
                LocalDate start = r.startDate() == null ? today : r.startDate();
                ps.addValue("sd", java.sql.Date.valueOf(start));
                jdbc.update("insert into membership (id, organization_id, branch_id, person_id, kind, status, current, start_date, origin, requested_by, created_at, created_by)"
                        + " values (:id, :o, :b, :p, :k, 'ACTIVE', true, :sd, 'RECORD', :rq, :at, :by)", ps);
            } else {
                jdbc.update("insert into membership (id, organization_id, branch_id, person_id, kind, status, current, start_date, origin, requested_by, created_at, created_by)"
                        + " values (:id, :o, :b, :p, :k, 'PENDING', false, :sd, 'REQUEST', :rq, :at, :by)", ps);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("membershipId", id.toString());
                payload.put("kind", kind);
                payload.put("personName", p.name());
                payload.put("branchName", support.branchName(branch));
                UUID approval = engine.open(new ApprovalEngine.NewRequest(scope.organizationId(), branch, null, "RITE_MEMBERSHIP", "PERSON", p.id(), scope.personId(), null, payload));
                jdbc.update("update membership set approval_id = :a where id = :id", new MapSqlParameterSource("a", approval).addValue("id", id));
            }
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.rite.membershipCurrentExists", HttpStatus.UNPROCESSABLE_ENTITY, p.name());
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("person", p.id().toString());
        d.put("kind", kind);
        d.put("mode", record ? "RECORD" : "REQUEST");
        audit.record(new AuditService.Command(MODULE, record ? "RECORD" : "REQUEST", ENTITY, id, scope.organizationId(), branch, d));
        return get(actor, scope, id);
    }

    // ---------------------------------------------------------------- edición y cierre

    @Transactional
    public RiteDtos.MembershipResponse update(AuthenticatedActor actor, AccessScope scope, UUID id, RiteDtos.MembershipUpdate r) {
        authz.require(actor, MODULE, Action.E);
        Row row = lock(scope, id);
        if (r == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "datos");
        }
        if (!"PENDING".equals(row.status()) && !"ACTIVE".equals(row.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, row.status());
        }
        String kind = r.kind() == null || r.kind().isBlank() ? row.kind() : kind(r.kind());
        LocalDate start = r.startDate() == null ? row.startDate() : r.startDate();
        if (start != null && start.isAfter(support.today(row.branchId()))) {
            throw new Exceptions("error.common.futureDate", HttpStatus.BAD_REQUEST);
        }
        int n = jdbc.update("update membership set kind = :k, start_date = :sd, updated_at = :at, updated_by = :by, version = version + 1 where id = :id and (:v is null or version = :v)",
                new MapSqlParameterSource("k", kind).addValue("sd", start == null ? null : java.sql.Date.valueOf(start)).addValue("at", Timestamp.from(clock.instant()))
                        .addValue("by", scope.personId()).addValue("id", id).addValue("v", r.version()));
        if (n == 0) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("kind", kind);
        d.put("startDate", String.valueOf(start));
        audit.record(new AuditService.Command(MODULE, "UPDATE", ENTITY, id, scope.organizationId(), row.branchId(), d));
        return get(actor, scope, id);
    }

    /** Termina la membresía vigente [V4]: motivo obligatorio; la salida disciplinaria pide H y notas. */
    @Transactional
    public RiteDtos.MembershipResponse end(AuthenticatedActor actor, AccessScope scope, UUID id, RiteDtos.EndRequest r) {
        authz.require(actor, MODULE, Action.E);
        Row row = lock(scope, id);
        if (r == null || !RiteSupport.hasText(r.exitReason())) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "motivo de salida");
        }
        String reason = r.exitReason().trim().toUpperCase();
        if (!EXITS.contains(reason)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "motivo de salida");
        }
        if (!"ACTIVE".equals(row.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, row.status());
        }
        String notes = RiteSupport.trim(r.exitNotes(), 1000);
        if ("DISCIPLINARY".equals(reason)) {
            authz.require(actor, MODULE, Action.H);
            if (notes == null || notes.length() < 3) {
                throw new Exceptions("error.rite.notesRequired", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
        LocalDate today = support.today(row.branchId());
        LocalDate end = r.endDate() == null ? today : r.endDate();
        if (end.isAfter(today)) {
            throw new Exceptions("error.common.futureDate", HttpStatus.BAD_REQUEST);
        }
        if (row.startDate() != null && end.isBefore(row.startDate())) {
            throw new Exceptions("error.common.dateRange", HttpStatus.BAD_REQUEST);
        }
        jdbc.update("update membership set status = 'ENDED', current = false, end_date = :ed, exit_reason = :er, exit_notes = :en, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("ed", java.sql.Date.valueOf(end)).addValue("er", reason).addValue("en", notes == null ? null : cipher.encrypt(notes))
                        .addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", id));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("exitReason", reason);
        d.put("endDate", end.toString());
        audit.record(new AuditService.Command(MODULE, "END", ENTITY, id, scope.organizationId(), row.branchId(), d));
        return get(actor, scope, id);
    }

    /** Cancela una solicitud pendiente con motivo (la aprobación pendiente se cierra con ella). */
    @Transactional
    public RiteDtos.MembershipResponse cancel(AuthenticatedActor actor, AccessScope scope, UUID id, String reasonText) {
        authz.require(actor, MODULE, Action.E);
        Row row = lock(scope, id);
        String reason = RiteSupport.trim(reasonText, 300);
        if (reason == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "motivo");
        }
        if (!"PENDING".equals(row.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, row.status());
        }
        jdbc.update("update membership set cancel_reason = :r where id = :id", new MapSqlParameterSource("r", reason).addValue("id", id));
        if (row.approvalId() != null) {
            engine.decideInternal(row.approvalId(), false, scope.personId(), reason);
        } else {
            markCancelled(id, reason);
        }
        audit.record(new AuditService.Command(MODULE, "CANCEL", ENTITY, id, scope.organizationId(), row.branchId(), Map.of("reason", reason)));
        return get(actor, scope, id);
    }

    // ---------------------------------------------------------------- aprobación

    @Transactional
    public RiteDtos.MembershipResponse approve(AuthenticatedActor actor, AccessScope scope, UUID id, String note) {
        Row row = lock(scope, id);
        engine.approve(actor, scope, approvalOf(row), note);
        return get(actor, scope, id);
    }

    @Transactional
    public RiteDtos.MembershipResponse reject(AuthenticatedActor actor, AccessScope scope, UUID id, String reason) {
        Row row = lock(scope, id);
        engine.reject(actor, scope, approvalOf(row), reason);
        return get(actor, scope, id);
    }

    private UUID approvalOf(Row row) {
        if (row.approvalId() == null || !"PENDING".equals(row.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, row.status());
        }
        return row.approvalId();
    }

    /** Lo llama el handler al aprobarse la solicitud (misma transacción de la decisión). */
    @Transactional
    public void onApproved(ApprovalHandler.ApprovalRow request, UUID byPerson) {
        UUID id = UUID.fromString(String.valueOf(request.payload().get("membershipId")));
        Row row = jdbc.query(SELECT + " where m.id = :id for update of m", new MapSqlParameterSource("id", id), (rs, i) -> row(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        if (!"PENDING".equals(row.status())) {
            throw new Exceptions("error.common.alreadyDecided", HttpStatus.CONFLICT);
        }
        RiteDtos.Checklist c = requirements.checklist(row.orgId(), TYPE, id, row.birthDate() == null ? List.of() : List.of(row.birthDate()), support.today(row.branchId()), row.overrideReason());
        if (!c.complete() && !c.overridden()) {
            throw new Exceptions("error.rite.requirementsPending", HttpStatus.UNPROCESSABLE_ENTITY, String.join(", ", requirements.unmet(c)));   // [V10]
        }
        Integer cur = jdbc.queryForObject("select count(*) from membership where person_id = :p and current", new MapSqlParameterSource("p", row.personId()), Integer.class);
        if (cur != null && cur > 0) {
            throw new Exceptions("error.rite.membershipCurrentExists", HttpStatus.UNPROCESSABLE_ENTITY, "");
        }
        LocalDate today = support.today(row.branchId());
        jdbc.update("update membership set status = 'ACTIVE', current = true, start_date = coalesce(start_date, :t), updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("t", java.sql.Date.valueOf(today)).addValue("at", Timestamp.from(clock.instant())).addValue("by", byPerson).addValue("id", id));
        audit.record(new AuditService.Command(MODULE, "APPROVE", ENTITY, id, row.orgId(), row.branchId(), Map.of("kind", row.kind())));
    }

    /** Rechazo o cancelación de la solicitud: la membresía queda CANCELLED con el motivo. */
    @Transactional
    public void onClosed(ApprovalHandler.ApprovalRow request, String note) {
        UUID id = UUID.fromString(String.valueOf(request.payload().get("membershipId")));
        markCancelled(id, note == null ? "-" : note);
    }

    private void markCancelled(UUID id, String reason) {
        jdbc.update("update membership set status = 'CANCELLED', current = false, cancel_reason = coalesce(cancel_reason, :r), updated_at = :at, version = version + 1"
                        + " where id = :id and status = 'PENDING'",
                new MapSqlParameterSource("r", RiteSupport.trim(reason, 300)).addValue("at", Timestamp.from(clock.instant())).addValue("id", id));
    }

    // ---------------------------------------------------------------- requisitos y excepción

    @Transactional(readOnly = true)
    public RiteDtos.Checklist checklist(AuthenticatedActor actor, AccessScope scope, UUID id) {
        get(actor, scope, id);                                                                                                   // visibilidad
        Row row = jdbc.query(SELECT + " where m.id = :id", new MapSqlParameterSource("id", id), (rs, i) -> row(rs)).get(0);
        return requirements.checklist(row.orgId(), TYPE, id, row.birthDate() == null ? List.of() : List.of(row.birthDate()), support.today(row.branchId()), row.overrideReason());
    }

    @Transactional
    public RiteDtos.Checklist mark(AuthenticatedActor actor, AccessScope scope, UUID id, UUID requirementId, RiteDtos.CheckRequest r) {
        authz.require(actor, MODULE, Action.E);
        Row row = lock(scope, id);
        if (!"PENDING".equals(row.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, row.status());
        }
        requirements.mark(actor, scope, TYPE, id, requirementId, r);
        return checklist(actor, scope, id);
    }

    /** Omite los requisitos con un motivo (acción O) y queda auditado [V10]. */
    @Transactional
    public RiteDtos.MembershipResponse override(AuthenticatedActor actor, AccessScope scope, UUID id, RiteDtos.OverrideRequest r) {
        authz.require(actor, MODULE, Action.O);
        Row row = lock(scope, id);
        String reason = RiteSupport.trim(r == null ? null : r.reason(), 300);
        if (reason == null || reason.length() < 5) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "motivo");
        }
        if (!"PENDING".equals(row.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, row.status());
        }
        jdbc.update("update membership set override_reason = :r, override_by = :by, updated_at = :at, version = version + 1 where id = :id",
                new MapSqlParameterSource("r", reason).addValue("by", scope.personId()).addValue("at", Timestamp.from(clock.instant())).addValue("id", id));
        audit.record(new AuditService.Command(MODULE, "OVERRIDE", ENTITY, id, scope.organizationId(), row.branchId(), Map.of("reason", reason)));
        return get(actor, scope, id);
    }

    // ---------------------------------------------------------------- utilidades

    private Row lock(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String w = RiteSupport.writable(scope, ps, "m");
        List<Row> rows = jdbc.query(SELECT + " where m.id = :id and " + w + " for update of m", ps, (rs, i) -> row(rs));
        if (rows.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return rows.get(0);
    }

    private static Row row(ResultSet rs) throws SQLException {
        java.sql.Date b = rs.getDate("birth_date");
        return new Row((UUID) rs.getObject("id"), (UUID) rs.getObject("organization_id"), (UUID) rs.getObject("branch_id"), (UUID) rs.getObject("person_id"), rs.getString("kind"),
                rs.getString("status"), rs.getBoolean("current"), date(rs, "start_date"), rs.getString("override_reason"), (UUID) rs.getObject("approval_id"), rs.getLong("version"),
                b == null ? null : b.toLocalDate());
    }

    private static String kind(String raw) {
        String k = raw == null || raw.isBlank() ? "MEMBER" : raw.trim().toUpperCase();
        if (!KINDS.contains(k)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo");
        }
        return k;
    }

    /** Membresías vigentes de la persona en la lista de sedes: usado por la ficha. */
    List<UUID> currentIds(UUID personId) {
        return new ArrayList<>(jdbc.queryForList("select id from membership where person_id = :p and current", new MapSqlParameterSource("p", personId), UUID.class));
    }
}
