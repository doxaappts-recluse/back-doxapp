package pe.dcs.app.features.pastoral.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.auth.service.SecretCipher;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
import pe.dcs.app.features.pastoral.dto.PastoralDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;
import pe.dcs.app.util.pagination.SortRequest;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * M12 · Casos de cuidado pastoral: seguimiento de una persona (INACTIVE_FOLLOWUP, VISIT, COUNSELING, CRISIS, BEREAVEMENT,
 * HOSPITAL, OTHER) con un responsable, notas (algunas reservadas) y contactos ({@code follow_up_contact} de M07, sin duplicar).
 * Alcance: organización + sedes visibles; ORG_USER solo ve sus casos asignados (OWN). Confidencialidad RESTRICTED y notas
 * reservadas solo las ve quien tenga la acción H o esté asignado al caso [T04].
 */
@Service
@RequiredArgsConstructor
public class PastoralCaseService {

    static final String MODULE = "PASTORAL_CARE";
    private static final String ENTITY = "PastoralCase";
    private static final Set<String> TYPES = Set.of("INACTIVE_FOLLOWUP", "VISIT", "COUNSELING", "CRISIS", "BEREAVEMENT", "HOSPITAL", "OTHER");
    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH");
    private static final Set<String> STATUSES = Set.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED");
    private static final Set<String> CONFIDENTIALITY = Set.of("STANDARD", "RESTRICTED");
    private static final Set<String> OPEN = Set.of("OPEN", "IN_PROGRESS");
    private static final Set<String> METHODS = Set.of("CALL", "WHATSAPP", "VISIT", "IN_PERSON", "OTHER");
    private static final Set<String> RESULTS = Set.of("CONTACTED", "NO_ANSWER", "RESCHEDULED", "REFUSED", "OTHER");

    private final NamedParameterJdbcTemplate jdbc;
    private final NotificationService notifications;
    private final PastoralRulesService rules;
    private final AuditService audit;
    private final AuthorizationService authorization;
    private final SecretCipher cipher;
    private final Clock clock;

    record Row(UUID id, UUID orgId, UUID branchId, UUID personId, String type, String priority, String status, UUID assignedTo, String source,
               String confidentiality, Instant dueAt, String result, Instant lastContactAt, Instant resolvedAt, Instant closedAt,
               Instant createdAt, long version) {
    }

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public PageResponse<PastoralDtos.CaseSummary> search(AuthenticatedActor actor, AccessScope scope, PastoralDtos.CaseSearch req) {
        PastoralDtos.CaseSearch.CaseFilters f = req == null || req.filters() == null
                ? new PastoralDtos.CaseSearch.CaseFilters(null, null, null, null, null, null, null, null, null, null) : req.filters();
        Instant now = clock.instant();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(visible(scope, hasH(actor), ps, "c"));
        ps.addValue("now", Timestamp.from(now));

        if (hasText(f.type())) {
            String t = f.type().trim().toUpperCase();
            if (!TYPES.contains(t)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo");
            }
            w.append(" and c.type = :type");
            ps.addValue("type", t);
        }
        if (hasText(f.status())) {
            String s = f.status().trim().toUpperCase();
            if (!STATUSES.contains(s)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
            }
            w.append(" and c.status = :status");
            ps.addValue("status", s);
        }
        if (hasText(f.priority())) {
            String p = f.priority().trim().toUpperCase();
            if (!PRIORITIES.contains(p)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "prioridad");
            }
            w.append(" and c.priority = :priority");
            ps.addValue("priority", p);
        }
        if (Boolean.TRUE.equals(f.open())) {
            w.append(" and c.status in ('OPEN','IN_PROGRESS')");
        }
        if (f.branchId() != null) {
            w.append(" and c.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (Boolean.TRUE.equals(f.mine())) {
            w.append(" and c.assigned_to = :meFilter");
            ps.addValue("meFilter", scope.personId());
        } else if (f.assignedTo() != null) {
            w.append(" and c.assigned_to = :fa");
            ps.addValue("fa", f.assignedTo());
        }
        if (Boolean.TRUE.equals(f.unassigned())) {
            w.append(" and c.assigned_to is null");
        }
        if (Boolean.TRUE.equals(f.overdue())) {
            w.append(" and c.status in ('OPEN','IN_PROGRESS') and c.due_at is not null and c.due_at < cast(:now as timestamptz)");
        }
        if (hasText(f.q())) {
            String[] words = f.q().trim().toLowerCase().split("\\s+");
            for (int i = 0; i < Math.min(words.length, 5); i++) {
                String k = "w" + i;
                ps.addValue(k, "%" + words[i].replace("%", "").replace("_", "") + "%");
                w.append(" and lower(p.first_name || ' ' || p.last_name) like :").append(k);
            }
        }
        String from = " from pastoral_case c join person p on p.id = c.person_id join branch b on b.id = c.branch_id"
                + " left join person ap on ap.id = c.assigned_to where ";
        Long total = jdbc.queryForObject("select count(*)" + from + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<PastoralDtos.CaseSummary> rows = jdbc.query(SUMMARY + from + w + " order by " + orderBy(req == null ? null : req.sorts()) + " limit :lim offset :off",
                ps, (rs, i) -> summary(rs, now));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    @Transactional(readOnly = true)
    public PastoralDtos.CaseResponse get(AuthenticatedActor actor, AccessScope scope, UUID id) {
        return build(actor, scope, load(scope, hasH(actor), id));
    }

    // ---------------------------------------------------------------- alta

    @Transactional
    public PastoralDtos.CaseResponse create(AuthenticatedActor actor, AccessScope scope, PastoralDtos.CaseCreateRequest r) {
        if (r == null || r.personId() == null || r.branchId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "persona y sede");
        }
        String branchName = activeBranchName(scope, r.branchId());
        assertPersonInOrg(scope.organizationId(), r.personId());
        String type = hasText(r.type()) ? r.type().trim().toUpperCase() : "OTHER";
        if (!TYPES.contains(type)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo");
        }
        String priority = hasText(r.priority()) ? r.priority().trim().toUpperCase() : "NORMAL";
        if (!PRIORITIES.contains(priority)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "prioridad");
        }
        String confidentiality = hasText(r.confidentiality()) ? r.confidentiality().trim().toUpperCase() : "STANDARD";
        if (!CONFIDENTIALITY.contains(confidentiality)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "confidencialidad");
        }
        UUID assignedTo = r.assignedTo();
        boolean selfAssigned = false;
        if (assignedTo == null && scope.role() == RoleType.ORG_USER && scope.personId() != null) {
            assignedTo = scope.personId();
            selfAssigned = true;
        } else if (assignedTo != null) {
            assertPersonInOrg(scope.organizationId(), assignedTo);
        }
        Instant due = r.dueAt() != null ? r.dueAt().atStartOfDay(ZoneOffset.UTC).toInstant() : defaultDueAt(scope.organizationId(), priority);
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(clock.instant());
        try {
            jdbc.update("insert into pastoral_case (id, organization_id, branch_id, person_id, type, priority, status, assigned_to, source,"
                            + " confidentiality, due_at, created_at, created_by, updated_at, updated_by)"
                            + " values (:id, :o, :b, :p, :t, :pr, 'OPEN', :a, 'MANUAL', :c, :due, :at, :by, :at, :by)",
                    new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("b", r.branchId()).addValue("p", r.personId())
                            .addValue("t", type).addValue("pr", priority).addValue("a", assignedTo).addValue("c", confidentiality)
                            .addValue("due", due == null ? null : Timestamp.from(due)).addValue("at", now).addValue("by", scope.personId()));
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.pastoral.openCaseExists", HttpStatus.CONFLICT);
        }
        if (hasText(r.initialNote())) {
            insertNote(scope, id, r.initialNote(), false);
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("branch", branchName);
        d.put("type", type);
        audit.record(new AuditService.Command(MODULE, "CREATE", ENTITY, id, scope.organizationId(), r.branchId(), d));
        if (assignedTo != null && !selfAssigned && !assignedTo.equals(scope.personId())) {
            notifyAssigned(scope.organizationId(), assignedTo, r.personId(), branchName, id);
        }
        return build(actor, scope, load(scope, hasH(actor), id));
    }

    // ---------------------------------------------------------------- cambios

    @Transactional
    public PastoralDtos.CaseResponse assign(AuthenticatedActor actor, AccessScope scope, UUID id, PastoralDtos.AssignRequest r) {
        Row c = load(scope, hasH(actor), id);
        assertOpen(c);
        UUID to = r == null ? null : r.assignedTo();
        if (to != null) {
            assertPersonInOrg(c.orgId(), to);
        }
        if (Objects.equals(to, c.assignedTo())) {
            return build(actor, scope, c);
        }
        if (c.assignedTo() != null && (r == null || !hasText(r.reason()))) {                                             // [V4]
            throw new Exceptions("error.pastoral.reassignReasonRequired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("update pastoral_case set assigned_to = :a, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("a", to).addValue("at", now).addValue("by", scope.personId()).addValue("id", id));
        jdbc.update("insert into case_assignment_history (id, case_id, from_person_id, to_person_id, reason, by_person_id, at)"
                        + " values (:id, :c, :f, :t, :r, :by, :at)",
                new MapSqlParameterSource("id", UUID.randomUUID()).addValue("c", id).addValue("f", c.assignedTo()).addValue("t", to)
                        .addValue("r", r == null ? null : clean(r.reason(), 300)).addValue("by", scope.personId()).addValue("at", now));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("to", to == null ? null : personName(to));
        audit.record(new AuditService.Command(MODULE, "ASSIGN", ENTITY, id, c.orgId(), c.branchId(), d));
        if (to != null && !to.equals(scope.personId())) {
            notifyAssigned(c.orgId(), to, c.personId(), branchName(c.branchId()), id);
        }
        return build(actor, scope, load(scope, hasH(actor), id));
    }

    @Transactional
    public PastoralDtos.CaseResponse addContact(AuthenticatedActor actor, AccessScope scope, UUID id, PastoralDtos.ContactRequest r) {
        Row c = load(scope, hasH(actor), id);
        assertOpen(c);
        if (r == null || !hasText(r.method()) || !hasText(r.result())) {
            throw new Exceptions("error.pastoral.contactResultRequired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String method = r.method().trim().toUpperCase();
        String result = r.result().trim().toUpperCase();
        if (!METHODS.contains(method) || !RESULTS.contains(result)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "contacto");
        }
        LocalDate today = LocalDate.now(clock);
        if (r.nextActionDate() != null && r.nextActionDate().isBefore(today)) {
            throw new Exceptions("error.common.pastDate", HttpStatus.BAD_REQUEST);
        }
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("insert into follow_up_contact (id, organization_id, subject_type, subject_id, at, method, result, notes, next_action_date, by_person_id, created_at)"
                        + " values (:id, :o, 'PASTORAL_CASE', :s, :at, :m, :r, :n, :na, :by, :at)",
                new MapSqlParameterSource("id", UUID.randomUUID()).addValue("o", c.orgId()).addValue("s", id).addValue("at", now).addValue("m", method)
                        .addValue("r", result).addValue("n", clean(r.notes(), 1000))
                        .addValue("na", r.nextActionDate() == null ? null : java.sql.Date.valueOf(r.nextActionDate())).addValue("by", scope.personId()));
        jdbc.update("update pastoral_case set last_contact_at = :at, status = case when status = 'OPEN' then 'IN_PROGRESS' else status end,"
                        + " updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", now).addValue("by", scope.personId()).addValue("id", id));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("method", method);
        d.put("result", result);
        audit.record(new AuditService.Command(MODULE, "CONTACT", ENTITY, id, c.orgId(), c.branchId(), d));
        return build(actor, scope, load(scope, hasH(actor), id));
    }

    @Transactional
    public PastoralDtos.CaseResponse addNote(AuthenticatedActor actor, AccessScope scope, UUID id, PastoralDtos.NoteRequest r) {
        Row c = load(scope, hasH(actor), id);
        if (r == null || !hasText(r.text())) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "nota");
        }
        boolean restricted = Boolean.TRUE.equals(r.restricted());
        if (restricted && !hasH(actor)) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        insertNote(scope, id, r.text(), restricted);
        audit.record(new AuditService.Command(MODULE, "NOTE", ENTITY, id, c.orgId(), c.branchId(), Map.of("restricted", restricted)));
        return build(actor, scope, load(scope, hasH(actor), id));
    }

    private void insertNote(AccessScope scope, UUID caseId, String text, boolean restricted) {
        jdbc.update("insert into case_note (id, case_id, author_id, text, restricted, created_at) values (:id, :c, :a, :t, :r, :at)",
                new MapSqlParameterSource("id", UUID.randomUUID()).addValue("c", caseId).addValue("a", scope.personId())
                        .addValue("t", cipher.encrypt(clean(text, 2000))).addValue("r", restricted).addValue("at", Timestamp.from(clock.instant())));
    }

    /** Requiere un resultado de catálogo (CASE_RESULT) [V5]. Solo desde OPEN/IN_PROGRESS. */
    @Transactional
    public PastoralDtos.CaseResponse resolve(AuthenticatedActor actor, AccessScope scope, UUID id, PastoralDtos.ResolveRequest r) {
        Row c = load(scope, hasH(actor), id);
        assertOpen(c);
        if (r == null || !hasText(r.result())) {
            throw new Exceptions("error.pastoral.resultRequired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String result = catalogCode(c.orgId(), "CASE_RESULT", r.result(), "resultado");
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("update pastoral_case set status = 'RESOLVED', result = :r, resolved_at = :at, updated_at = :at, updated_by = :by,"
                        + " version = version + 1 where id = :id",
                new MapSqlParameterSource("r", result).addValue("at", now).addValue("by", scope.personId()).addValue("id", id));
        if (hasText(r.notes())) {
            insertNote(scope, id, r.notes(), false);
        }
        audit.record(new AuditService.Command(MODULE, "RESOLVE", ENTITY, id, c.orgId(), c.branchId(), Map.of("result", result)));
        return build(actor, scope, load(scope, hasH(actor), id));
    }

    /** RESOLVED → CLOSED; cualquier otro estado responde 409 [T03]. */
    @Transactional
    public PastoralDtos.CaseResponse close(AuthenticatedActor actor, AccessScope scope, UUID id) {
        Row c = load(scope, hasH(actor), id);
        if (!"RESOLVED".equals(c.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, c.status());
        }
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("update pastoral_case set status = 'CLOSED', closed_at = :at, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", now).addValue("by", scope.personId()).addValue("id", id));
        audit.record(new AuditService.Command(MODULE, "CLOSE", ENTITY, id, c.orgId(), c.branchId(), Map.of()));
        return build(actor, scope, load(scope, hasH(actor), id));
    }

    // ---------------------------------------------------------------- alcance y utilidades

    /** Alcance de filas: organización, sedes visibles, OWN para ORG_USER, y RESTRICTED oculto sin H salvo caso propio. */
    static String visible(AccessScope scope, boolean hasH, MapSqlParameterSource ps, String a) {
        StringBuilder sb = new StringBuilder(a + ".organization_id = :org");
        ps.addValue("org", scope.organizationId());
        if (!scope.allBranches()) {
            List<UUID> ids = scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
            ps.addValue("scopeBranches", ids);
            sb.append(" and ").append(a).append(".branch_id in (:scopeBranches)");
        }
        UUID me = scope.personId() == null ? new UUID(0, 0) : scope.personId();
        if (scope.role() == RoleType.ORG_USER) {
            ps.addValue("meOwn", me);
            sb.append(" and ").append(a).append(".assigned_to = :meOwn");
        } else if (!hasH) {
            ps.addValue("meRestricted", me);
            sb.append(" and (").append(a).append(".confidentiality = 'STANDARD' or ").append(a).append(".assigned_to = :meRestricted)");
        }
        return sb.toString();
    }

    private boolean hasH(AuthenticatedActor actor) {
        return authorization.effectiveActions(actor, MODULE).contains("H");
    }

    Row load(AccessScope scope, boolean hasH, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String vis = visible(scope, hasH, ps, "c");
        return jdbc.query("select c.* from pastoral_case c where c.id = :id and " + vis, ps, (rs, i) -> row(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private static Row row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Row((UUID) rs.getObject("id"), (UUID) rs.getObject("organization_id"), (UUID) rs.getObject("branch_id"), (UUID) rs.getObject("person_id"),
                rs.getString("type"), rs.getString("priority"), rs.getString("status"), (UUID) rs.getObject("assigned_to"), rs.getString("source"),
                rs.getString("confidentiality"), instant(rs.getTimestamp("due_at")), rs.getString("result"), instant(rs.getTimestamp("last_contact_at")),
                instant(rs.getTimestamp("resolved_at")), instant(rs.getTimestamp("closed_at")), instant(rs.getTimestamp("created_at")), rs.getLong("version"));
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static final String SUMMARY = "select c.id, c.person_id, p.first_name, p.last_name, c.branch_id, b.name as branch_name, c.type,"
            + " c.priority, c.status, c.assigned_to, ap.first_name as a_first, ap.last_name as a_last, c.source, c.confidentiality, c.due_at,"
            + " c.last_contact_at, c.created_at";

    private PastoralDtos.CaseSummary summary(java.sql.ResultSet rs, Instant now) throws java.sql.SQLException {
        String status = rs.getString("status");
        Timestamp due = rs.getTimestamp("due_at");
        boolean overdue = OPEN.contains(status) && due != null && due.toInstant().isBefore(now);
        String af = rs.getString("a_first");
        return new PastoralDtos.CaseSummary((UUID) rs.getObject("id"), (UUID) rs.getObject("person_id"),
                (rs.getString("first_name") + " " + rs.getString("last_name")).trim(), (UUID) rs.getObject("branch_id"), rs.getString("branch_name"),
                rs.getString("type"), rs.getString("priority"), status, (UUID) rs.getObject("assigned_to"),
                af == null ? null : (af + " " + rs.getString("a_last")).trim(), rs.getString("source"), rs.getString("confidentiality"),
                due == null ? null : due.toInstant().atZone(ZoneOffset.UTC).toLocalDate(), instant(rs.getTimestamp("last_contact_at")), overdue,
                instant(rs.getTimestamp("created_at")));
    }

    private static String orderBy(List<SortRequest> sorts) {
        String key = sorts == null || sorts.isEmpty() ? "createdAt" : String.valueOf(sorts.get(0).getResolvedField());
        boolean desc = sorts == null || sorts.isEmpty() || "DESC".equalsIgnoreCase(sorts.get(0).getDirection());
        String dir = desc ? " desc" : " asc";
        String expr = switch (key) {
            case "name" -> "lower(p.last_name)" + dir + ", lower(p.first_name)" + dir;
            case "priority" -> "c.priority" + dir;
            case "status" -> "c.status" + dir;
            case "dueAt" -> "c.due_at" + dir + " nulls last";
            default -> "c.created_at" + dir;
        };
        return expr + ", c.id";
    }

    private PastoralDtos.CaseResponse build(AuthenticatedActor actor, AccessScope scope, Row c) {
        Instant now = clock.instant();
        PastoralDtos.CaseSummary s = jdbc.query(SUMMARY + " from pastoral_case c join person p on p.id = c.person_id join branch b on b.id = c.branch_id"
                + " left join person ap on ap.id = c.assigned_to where c.id = :id", new MapSqlParameterSource("id", c.id()), (rs, i) -> summary(rs, now)).get(0);
        boolean hasH = hasH(actor);
        boolean assignedToMe = c.assignedTo() != null && c.assignedTo().equals(scope.personId());
        List<PastoralDtos.NoteView> notes = jdbc.query("select n.id, n.author_id, n.text, n.restricted, n.created_at, p.first_name, p.last_name"
                + " from case_note n left join person p on p.id = n.author_id where n.case_id = :id order by n.created_at desc",
                new MapSqlParameterSource("id", c.id()), (rs, i) -> {
                    boolean restricted = rs.getBoolean("restricted");
                    UUID authorId = (UUID) rs.getObject("author_id");
                    boolean visible = !restricted || hasH || assignedToMe || Objects.equals(authorId, scope.personId());
                    String fn = rs.getString("first_name");
                    return new PastoralDtos.NoteView((UUID) rs.getObject("id"), authorId, fn == null ? null : (fn + " " + rs.getString("last_name")).trim(),
                            visible ? cipher.decrypt(rs.getString("text")) : null, restricted, rs.getTimestamp("created_at").toInstant());
                }).stream().filter(n -> n.restricted() ? (hasH || assignedToMe || Objects.equals(n.authorId(), scope.personId())) : true).toList();
        List<PastoralDtos.ContactView> contacts = jdbc.query("select f.id, f.at, f.method, f.result, f.notes, f.next_action_date, f.by_person_id, p.first_name, p.last_name"
                + " from follow_up_contact f left join person p on p.id = f.by_person_id where f.subject_type = 'PASTORAL_CASE' and f.subject_id = :id order by f.at desc",
                new MapSqlParameterSource("id", c.id()), (rs, i) -> {
                    java.sql.Date na = rs.getDate("next_action_date");
                    String fn = rs.getString("first_name");
                    return new PastoralDtos.ContactView((UUID) rs.getObject("id"), rs.getTimestamp("at").toInstant(), rs.getString("method"),
                            rs.getString("result"), rs.getString("notes"), na == null ? null : na.toLocalDate(), (UUID) rs.getObject("by_person_id"),
                            fn == null ? null : (fn + " " + rs.getString("last_name")).trim());
                });
        List<PastoralDtos.AssignmentView> history = jdbc.query("select h.id, h.from_person_id, fp.first_name as ff, fp.last_name as fl,"
                + " h.to_person_id, tp.first_name as tf, tp.last_name as tl, h.reason, h.by_person_id, h.at from case_assignment_history h"
                + " left join person fp on fp.id = h.from_person_id left join person tp on tp.id = h.to_person_id where h.case_id = :id order by h.at desc",
                new MapSqlParameterSource("id", c.id()), (rs, i) -> {
                    String ff = rs.getString("ff");
                    String tf = rs.getString("tf");
                    return new PastoralDtos.AssignmentView((UUID) rs.getObject("id"), (UUID) rs.getObject("from_person_id"),
                            ff == null ? null : (ff + " " + rs.getString("fl")).trim(), (UUID) rs.getObject("to_person_id"),
                            tf == null ? null : (tf + " " + rs.getString("tl")).trim(), rs.getString("reason"), (UUID) rs.getObject("by_person_id"),
                            rs.getTimestamp("at").toInstant());
                });
        return new PastoralDtos.CaseResponse(s, c.result(), c.resolvedAt(), c.closedAt(), notes, contacts, history, c.version());
    }

    private static void assertOpen(Row c) {
        if (!OPEN.contains(c.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, c.status());
        }
    }

    private Instant defaultDueAt(UUID orgId, String priority) {
        PastoralDtos.RulesResponse rr = rules.get(orgId);
        int hours = switch (priority) {
            case "HIGH" -> rr.slaHoursHigh();
            case "LOW" -> rr.slaHoursLow();
            default -> rr.slaHoursNormal();
        };
        return clock.instant().plusSeconds(hours * 3600L);
    }

    private String activeBranchName(AccessScope scope, UUID branchId) {
        List<String[]> rows = jdbc.query("select name, status from branch where id = :id and organization_id = :o",
                new MapSqlParameterSource("id", branchId).addValue("o", scope.organizationId()), (rs, i) -> new String[]{rs.getString(1), rs.getString(2)});
        if (rows.isEmpty() || !scope.canSeeBranch(branchId)) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        if (!"ACTIVE".equals(rows.get(0)[1])) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, rows.get(0)[1]);
        }
        return rows.get(0)[0];
    }

    private String branchName(UUID branchId) {
        List<String> n = jdbc.queryForList("select name from branch where id = :id", new MapSqlParameterSource("id", branchId), String.class);
        return n.isEmpty() ? "" : n.get(0);
    }

    String personName(UUID personId) {
        List<String> n = jdbc.queryForList("select trim(first_name || ' ' || last_name) from person where id = :id", new MapSqlParameterSource("id", personId), String.class);
        return n.isEmpty() ? "" : n.get(0);
    }

    private void assertPersonInOrg(UUID orgId, UUID personId) {
        List<String> st = jdbc.queryForList("select status from person where id = :id and organization_id = :o",
                new MapSqlParameterSource("id", personId).addValue("o", orgId), String.class);
        if (st.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        if (!"ACTIVE".equals(st.get(0))) {
            throw new Exceptions("error.pastoral.personInactive", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private String catalogCode(UUID orgId, String type, String raw, String label) {
        if (!hasText(raw)) {
            return null;
        }
        String code = raw.trim().toUpperCase();
        Integer n = jdbc.queryForObject("select count(*) from catalog_item where type = :t and code = :c and active and (organization_id is null or organization_id = :o)",
                new MapSqlParameterSource("t", type).addValue("c", code).addValue("o", orgId), Integer.class);
        if (n == null || n == 0) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, label);
        }
        return code;
    }

    private void notifyAssigned(UUID orgId, UUID assignedTo, UUID personId, String branch, UUID caseId) {
        notifications.toPersons(NotificationType.PASTORAL_CASE_ASSIGNED, orgId, List.of(assignedTo),
                Map.of("name", personName(personId), "branch", branch), "/app/pastoral-cases/" + caseId, null);
    }

    static String clean(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        if (t.length() > max) {
            throw new Exceptions("error.common.tooLong", HttpStatus.BAD_REQUEST, "texto", max);
        }
        return t;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
