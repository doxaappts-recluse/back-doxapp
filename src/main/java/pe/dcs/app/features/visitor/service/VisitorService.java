package pe.dcs.app.features.visitor.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
import pe.dcs.app.features.person.service.ConsentService;
import pe.dcs.app.features.person.service.PersonLookupService;
import pe.dcs.app.features.person.service.PersonService;
import pe.dcs.app.features.visitor.dto.VisitorDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.vo.DocumentType;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;
import pe.dcs.app.util.pagination.SortRequest;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M07 · Visitantes y consolidación. Una visita es un caso (NEW → IN_FOLLOWUP → INTEGRATED → CONVERTED | ARCHIVED) sobre una persona
 * de M06 (nunca se duplica: se propone vincular) con un responsable (consolidador) y contactos de seguimiento.
 * Alcance: organización + sedes visibles; quien es ORG_USER solo ve los casos que tiene asignados (OWN).
 * [V2] [V3] [V4] [V5] [V6] [V7] [V8] [V9] [V10].
 */
@Service
@RequiredArgsConstructor
public class VisitorService {

    static final String MODULE = "VISITOR";
    private static final String ENTITY = "VisitorCase";
    private static final Set<String> OPEN = Set.of("NEW", "IN_FOLLOWUP", "INTEGRATED");
    private static final Set<String> STAGES = Set.of("NEW", "IN_FOLLOWUP", "INTEGRATED", "CONVERTED", "ARCHIVED");
    private static final Set<String> METHODS = Set.of("CALL", "WHATSAPP", "VISIT", "IN_PERSON", "OTHER");
    private static final Set<String> RESULTS = Set.of("CONTACTED", "NO_ANSWER", "RESCHEDULED", "REFUSED", "OTHER");
    private static final int ADULT_AGE = 18;
    private static final int MAX_EXPORT_ROWS = 50_000;

    /** Lo implementa M08 (membresía) cuando exista: sin él ningún caso puede pasar a CONVERTED [V7]. */
    public interface MembershipConversionPort {
        boolean hasApprovedMembership(UUID organizationId, UUID personId);
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final PersonLookupService lookup;
    private final PersonService persons;
    private final NotificationService notifications;
    private final VisitorRulesService rules;
    private final AuditService audit;
    private final Clock clock;
    private final ObjectProvider<MembershipConversionPort> membership;
    private final ConsentService consents;

    /** Fila completa del caso. */
    record Row(UUID id, UUID orgId, UUID branchId, UUID personId, LocalDate firstVisit, String howArrived, UUID invitedBy, String stage,
               UUID consolidatorId, Instant firstContactAt, Instant lastContactAt, LocalDate nextAction, Instant integratedAt, Instant closedAt,
               String archiveReason, String notes, String source, String consentStatus, Instant consentAt, Instant createdAt, long version) {
    }

    public record ExportResult(byte[] content, int rows) {
    }

    /** Exporta los casos a XLSX (acción X) con los filtros y el alcance de la búsqueda. */
    @Transactional
    public ExportResult exportList(AccessScope scope, VisitorDtos.Search req) {
        List<VisitorDtos.Summary> all = new ArrayList<>();
        for (int page = 0; ; page++) {
            pe.dcs.app.util.pagination.PaginationRequest pg = new pe.dcs.app.util.pagination.PaginationRequest();
            pg.setPage(page);
            pg.setSize(200);
            PageResponse<VisitorDtos.Summary> r = search(scope, new VisitorDtos.Search(req == null ? null : req.filters(), pg, req == null ? null : req.sorts()));
            all.addAll(r.getContent());
            if (all.size() > MAX_EXPORT_ROWS) {
                throw new Exceptions("error.visitor.exportTooLarge", HttpStatus.UNPROCESSABLE_ENTITY, MAX_EXPORT_ROWS);
            }
            if (r.getContent().size() < 200) {
                break;
            }
        }
        List<String> headers = List.of("Visitante", "Teléfono", "Correo", "Sede", "Etapa", "Primera visita", "Cómo nos conoció", "Responsable",
                "Último contacto", "Próxima acción", "Vencido", "Origen");
        List<List<Object>> data = new ArrayList<>(all.size());
        for (VisitorDtos.Summary v : all) {
            data.add(java.util.Arrays.asList(v.fullName(), v.phone(), v.email(), v.branchName(), v.stage(), String.valueOf(v.firstVisitDate()), v.howArrived(),
                    v.consolidatorName(), v.lastContactAt() == null ? null : v.lastContactAt().toString(), v.nextActionDate() == null ? null : v.nextActionDate().toString(),
                    v.overdue() ? "Sí" : "No", v.source()));
        }
        byte[] bytes = pe.dcs.app.shared.export.XlsxWriter.write("Visitantes", headers, data);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("rows", all.size());
        audit.record(new AuditService.Command(MODULE, "EXPORT", ENTITY, null, scope.organizationId(), null, d));
        return new ExportResult(bytes, all.size());
    }

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public PageResponse<VisitorDtos.Summary> search(AccessScope scope, VisitorDtos.Search req) {
        VisitorDtos.Search.Filters f = req == null || req.filters() == null
                ? new VisitorDtos.Search.Filters(null, null, null, null, null, null, null, null) : req.filters();
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(visible(scope, ps, "c"));
        ps.addValue("today", java.sql.Date.valueOf(today)).addValue("now", Timestamp.from(now));

        if (hasText(f.stage())) {
            String st = f.stage().trim().toUpperCase();
            if (!STAGES.contains(st)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "etapa");
            }
            w.append(" and c.stage = :stage");
            ps.addValue("stage", st);
        }
        if (Boolean.TRUE.equals(f.open())) {
            w.append(" and c.stage in ('NEW','IN_FOLLOWUP','INTEGRATED')");
        }
        if (f.branchId() != null) {
            w.append(" and c.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (Boolean.TRUE.equals(f.mine())) {
            w.append(" and c.consolidator_id = :meFilter");
            ps.addValue("meFilter", scope.personId());
        } else if (f.consolidatorId() != null) {
            w.append(" and c.consolidator_id = :fc");
            ps.addValue("fc", f.consolidatorId());
        }
        if (Boolean.TRUE.equals(f.unassigned())) {
            w.append(" and c.consolidator_id is null");
        }
        if (Boolean.TRUE.equals(f.overdue())) {
            w.append(" and ((c.stage in ('NEW','IN_FOLLOWUP','INTEGRATED') and c.next_action_date < :today)")
                    .append(" or (c.stage = 'NEW' and c.first_contact_at is null and c.created_at < cast(:now as timestamptz) - make_interval(hours => coalesce(r.new_sla_hours, 48))))");
        }
        if (hasText(f.q())) {
            String[] words = f.q().trim().toLowerCase().split("\\s+");
            for (int i = 0; i < Math.min(words.length, 5); i++) {
                String k = "w" + i;
                ps.addValue(k, "%" + words[i].replace("%", "").replace("_", "") + "%");
                w.append(" and (lower(p.first_name || ' ' || p.last_name) like :").append(k)
                        .append(" or lower(coalesce(p.email, '')) like :").append(k)
                        .append(" or coalesce(p.phone, '') like :").append(k).append(")");
            }
        }
        String from = " from visitor_case c join person p on p.id = c.person_id join branch b on b.id = c.branch_id"
                + " left join person cp on cp.id = c.consolidator_id left join visitor_rules r on r.organization_id = c.organization_id where ";
        Long total = jdbc.queryForObject("select count(*)" + from + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<VisitorDtos.Summary> rows = jdbc.query(SUMMARY + from + w + " order by " + orderBy(req == null ? null : req.sorts()) + " limit :lim offset :off",
                ps, (rs, i) -> summary(rs, today, now));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    @Transactional(readOnly = true)
    public VisitorDtos.Response get(AccessScope scope, UUID id) {
        return toResponse(scope, load(scope, id));
    }

    /** Personas ya registradas que coinciden con los datos escritos, para proponer vincular en lugar de duplicar [V9]. */
    @Transactional(readOnly = true)
    public VisitorDtos.MatchResult matches(AccessScope scope, DocumentType docType, String doc, String phone, String email, UUID branchId) {
        PersonLookupService.Matches m = lookup.findMatches(scope, docType, doc, PersonService.normalizePhone(safePhone(phone)), email);
        List<VisitorDtos.Match> out = new ArrayList<>();
        for (PersonLookupService.PersonMin p : m.people()) {
            UUID open = branchId == null ? null : jdbc.query("select id from visitor_case where person_id = :p and branch_id = :b and stage in ('NEW','IN_FOLLOWUP','INTEGRATED')",
                    new MapSqlParameterSource("p", p.id()).addValue("b", branchId), (rs, i) -> (UUID) rs.getObject(1)).stream().findFirst().orElse(null);
            out.add(new VisitorDtos.Match(p.id(), p.fullName(), p.docType() == null ? null : p.docType().name(), p.docNumber(),
                    null, null, p.status(), p.branchName(), open));
        }
        return new VisitorDtos.MatchResult(enrichContacts(out), m.documentInOtherBranch());
    }

    // ---------------------------------------------------------------- alta

    @Transactional
    public VisitorDtos.Response create(AuthenticatedActor actor, AccessScope scope, VisitorDtos.CreateRequest r) {
        if (r == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "datos");
        }
        if (r.branchId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sede");
        }
        String branchName = activeBranchName(scope, r.branchId());
        LocalDate today = LocalDate.now(clock);
        LocalDate visit = r.firstVisitDate() == null ? today : r.firstVisitDate();
        if (visit.isAfter(today)) {                                                                                  // [V3]
            throw new Exceptions("error.common.futureDate", HttpStatus.BAD_REQUEST);
        }
        String how = catalogCode(scope.organizationId(), "VISITOR_SOURCE", r.howArrived(), "cómo llegó");
        if (r.invitedBy() != null) {
            assertPersonInOrg(scope.organizationId(), r.invitedBy());                                                 // [V4]
        }
        UUID consolidator = r.consolidatorId();
        boolean selfAssigned = false;
        if (consolidator == null && scope.role() == RoleType.ORG_USER && scope.personId() != null) {
            consolidator = scope.personId();                                                                          // quien registra lo sigue viendo (OWN)
            selfAssigned = true;
        } else if (consolidator != null) {
            checkConsolidator(scope.organizationId(), r.branchId(), consolidator);                                    // [V5]
        }

        UUID personId = r.personId();
        if (personId != null) {
            PersonLookupService.PersonMin p = lookup.getVisible(scope, personId);
            if (!"ACTIVE".equals(p.status())) {
                throw new Exceptions("error.visitor.personInactive", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        } else {
            if (!hasText(r.firstName()) || !hasText(r.lastName())) {
                throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "nombre");
            }
            String phone = PersonService.normalizePhone(safePhone(r.phone()));
            if (phone == null && !hasText(r.email())) {                                                               // [V2]
                throw new Exceptions("error.visitor.contactRequired", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            PersonLookupService.Matches m = lookup.findMatches(scope, r.docType(), r.docNumber(), phone, r.email());
            if (m.documentInOtherBranch()) {
                throw new Exceptions("error.person.existsOtherBranch", HttpStatus.CONFLICT);
            }
            if (!m.people().isEmpty() && !Boolean.TRUE.equals(r.ignoreMatches())) {                                     // [V9]
                throw new Exceptions("error.visitor.duplicateSuggested", HttpStatus.CONFLICT);
            }
            personId = persons.registerBasic(scope.organizationId(), r.branchId(), r.firstName(), r.lastName(), r.phone(), r.email(),
                    r.docType(), r.docNumber(), scope.personId(), "VISITOR");
        }
        boolean consent = Boolean.TRUE.equals(r.consentGranted());
        UUID id = insertCase(scope.organizationId(), r.branchId(), personId, visit, how, r.invitedBy(), consolidator, clean(r.notes(), 1000),
                "STAFF", consent, scope.personId());
        if (consent) {
            consents.grantDefaults(scope.organizationId(), personId, "VISITOR", scope.personId());                    // M06: consentimiento vigente
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("branch", branchName);
        d.put("source", "STAFF");
        audit.record(new AuditService.Command(MODULE, "CREATE", ENTITY, id, scope.organizationId(), r.branchId(), d));
        if (consolidator != null && !selfAssigned && !consolidator.equals(scope.personId())) {
            notifyAssigned(scope.organizationId(), consolidator, personId, branchName, id);
        }
        return toResponse(scope, load(scope, id));
    }

    /** Inserta el caso respetando [V10] (un caso abierto por persona y sede). Lo comparten el alta interna y el formulario público. */
    UUID insertCase(UUID orgId, UUID branchId, UUID personId, LocalDate visit, String how, UUID invitedBy, UUID consolidator, String notes,
                    String source, boolean consent, UUID byPerson) {
        Integer open = jdbc.queryForObject("select count(*) from visitor_case where person_id = :p and branch_id = :b and stage in ('NEW','IN_FOLLOWUP','INTEGRATED')",
                new MapSqlParameterSource("p", personId).addValue("b", branchId), Integer.class);
        if (open != null && open > 0) {
            throw new Exceptions("error.visitor.openCaseExists", HttpStatus.CONFLICT);
        }
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(clock.instant());
        try {
            jdbc.update("insert into visitor_case (id, organization_id, branch_id, person_id, first_visit_date, how_arrived, invited_by, stage,"
                            + " consolidator_id, assigned_at, notes, source, consent_status, consent_at, created_at, created_by)"
                            + " values (:id, :o, :b, :p, :v, :h, :i, 'NEW', :c, :ca, :n, :s, :cs, :cat, :at, :by)",
                    new MapSqlParameterSource("id", id).addValue("o", orgId).addValue("b", branchId).addValue("p", personId)
                            .addValue("v", java.sql.Date.valueOf(visit)).addValue("h", how).addValue("i", invitedBy).addValue("c", consolidator)
                            .addValue("ca", consolidator == null ? null : now).addValue("n", notes).addValue("s", source)
                            .addValue("cs", consent ? "GRANTED" : "PENDING").addValue("cat", consent ? now : null).addValue("at", now).addValue("by", byPerson));
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.visitor.openCaseExists", HttpStatus.CONFLICT);
        }
        return id;
    }

    // ---------------------------------------------------------------- cambios

    @Transactional
    public VisitorDtos.Response update(AuthenticatedActor actor, AccessScope scope, UUID id, VisitorDtos.UpdateRequest r) {
        Row c = load(scope, id);
        assertOpen(c);
        if (r == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "datos");
        }
        if (r.version() != null && r.version() != c.version()) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate visit = r.firstVisitDate() == null ? c.firstVisit() : r.firstVisitDate();
        if (visit.isAfter(today)) {
            throw new Exceptions("error.common.futureDate", HttpStatus.BAD_REQUEST);
        }
        if (r.nextActionDate() != null && r.nextActionDate().isBefore(today)) {
            throw new Exceptions("error.visitor.nextActionPast", HttpStatus.BAD_REQUEST);
        }
        String how = catalogCode(c.orgId(), "VISITOR_SOURCE", r.howArrived(), "cómo llegó");
        if (r.invitedBy() != null) {
            assertPersonInOrg(c.orgId(), r.invitedBy());
        }
        List<String> changed = new ArrayList<>();
        if (!visit.equals(c.firstVisit())) {
            changed.add("firstVisitDate");
        }
        if (!java.util.Objects.equals(how, c.howArrived())) {
            changed.add("howArrived");
        }
        if (!java.util.Objects.equals(r.invitedBy(), c.invitedBy())) {
            changed.add("invitedBy");
        }
        String notes = clean(r.notes(), 1000);
        if (!java.util.Objects.equals(notes, c.notes())) {
            changed.add("notes");
        }
        if (!java.util.Objects.equals(r.nextActionDate(), c.nextAction())) {
            changed.add("nextActionDate");
        }
        if (!changed.isEmpty()) {
            jdbc.update("update visitor_case set first_visit_date = :v, how_arrived = :h, invited_by = :i, notes = :n, next_action_date = :na,"
                            + " updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                    new MapSqlParameterSource("v", java.sql.Date.valueOf(visit)).addValue("h", how).addValue("i", r.invitedBy()).addValue("n", notes)
                            .addValue("na", r.nextActionDate() == null ? null : java.sql.Date.valueOf(r.nextActionDate()))
                            .addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", id));
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("fields", changed);
            audit.record(new AuditService.Command(MODULE, "UPDATE", ENTITY, id, c.orgId(), c.branchId(), d));
        }
        return toResponse(scope, load(scope, id));
    }

    @Transactional
    public VisitorDtos.Response assign(AuthenticatedActor actor, AccessScope scope, UUID id, VisitorDtos.AssignRequest r) {
        Row c = load(scope, id);
        assertOpen(c);
        UUID to = r == null ? null : r.consolidatorId();
        if (to != null) {
            checkConsolidator(c.orgId(), c.branchId(), to);                                                           // [V5]
        }
        if (java.util.Objects.equals(to, c.consolidatorId())) {
            return toResponse(scope, c);
        }
        jdbc.update("update visitor_case set consolidator_id = :c, assigned_at = :ca, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("c", to).addValue("ca", to == null ? null : Timestamp.from(clock.instant()))
                        .addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", id));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("to", to == null ? null : personName(to));
        audit.record(new AuditService.Command(MODULE, "ASSIGN", ENTITY, id, c.orgId(), c.branchId(), d));
        if (to != null && !to.equals(scope.personId())) {
            notifyAssigned(c.orgId(), to, c.personId(), branchName(c.branchId()), id);
        }
        // Quien tenía el caso como ORG_USER (OWN) y lo cede deja de verlo: se responde con el resumen sin releer el caso.
        Row after = loadRaw(id);
        return toResponseUnscoped(after);
    }

    @Transactional
    public VisitorDtos.Response addContact(AuthenticatedActor actor, AccessScope scope, UUID id, VisitorDtos.ContactRequest r) {
        Row c = load(scope, id);
        assertOpen(c);
        if (r == null || !hasText(r.method()) || !hasText(r.result())) {                                              // [V6]
            throw new Exceptions("error.visitor.contactResultRequired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String method = r.method().trim().toUpperCase();
        String result = r.result().trim().toUpperCase();
        if (!METHODS.contains(method) || !RESULTS.contains(result)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "contacto");
        }
        LocalDate today = LocalDate.now(clock);
        if (r.nextActionDate() != null && r.nextActionDate().isBefore(today)) {                                       // [V6]
            throw new Exceptions("error.visitor.nextActionPast", HttpStatus.BAD_REQUEST);
        }
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("insert into follow_up_contact (id, organization_id, subject_type, subject_id, at, method, result, notes, next_action_date, by_person_id, created_at)"
                        + " values (:id, :o, 'VISITOR_CASE', :s, :at, :m, :r, :n, :na, :by, :at)",
                new MapSqlParameterSource("id", UUID.randomUUID()).addValue("o", c.orgId()).addValue("s", id).addValue("at", now).addValue("m", method)
                        .addValue("r", result).addValue("n", clean(r.notes(), 1000))
                        .addValue("na", r.nextActionDate() == null ? null : java.sql.Date.valueOf(r.nextActionDate())).addValue("by", scope.personId()));
        jdbc.update("update visitor_case set last_contact_at = :at, first_contact_at = coalesce(first_contact_at, :at), next_action_date = :na,"
                        + " stage = case when stage = 'NEW' then 'IN_FOLLOWUP' else stage end, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", now).addValue("na", r.nextActionDate() == null ? null : java.sql.Date.valueOf(r.nextActionDate()))
                        .addValue("by", scope.personId()).addValue("id", id));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("method", method);
        d.put("result", result);
        audit.record(new AuditService.Command(MODULE, "CONTACT", ENTITY, id, c.orgId(), c.branchId(), d));
        return toResponse(scope, load(scope, id));
    }

    /** IN_FOLLOWUP → INTEGRATED: lo confirma una persona (con M09 el sistema solo lo sugerirá). */
    @Transactional
    public VisitorDtos.Response integrate(AuthenticatedActor actor, AccessScope scope, UUID id) {
        Row c = load(scope, id);
        if (!"IN_FOLLOWUP".equals(c.stage())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, c.stage());
        }
        jdbc.update("update visitor_case set stage = 'INTEGRATED', integrated_at = :at, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", id));
        audit.record(new AuditService.Command(MODULE, "INTEGRATE", ENTITY, id, c.orgId(), c.branchId(), Map.of("from", c.stage())));
        return toResponse(scope, load(scope, id));
    }

    /** INTEGRATED → CONVERTED. Exige membresía o solicitud aprobada (M08) [V7]; mientras M08 no exista, siempre 422. */
    @Transactional
    public VisitorDtos.Response convert(AuthenticatedActor actor, AccessScope scope, UUID id) {
        Row c = load(scope, id);
        if (!"INTEGRATED".equals(c.stage())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, c.stage());
        }
        MembershipConversionPort port = membership.getIfAvailable();
        if (port == null || !port.hasApprovedMembership(c.orgId(), c.personId())) {
            throw new Exceptions("error.visitor.convertRequirement", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        jdbc.update("update visitor_case set stage = 'CONVERTED', closed_at = :at, next_action_date = null, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", id));
        audit.record(new AuditService.Command(MODULE, "CONVERT", ENTITY, id, c.orgId(), c.branchId(), Map.of("from", c.stage())));
        return toResponse(scope, load(scope, id));
    }

    @Transactional
    public VisitorDtos.Response archive(AuthenticatedActor actor, AccessScope scope, UUID id, VisitorDtos.ArchiveRequest r) {
        Row c = load(scope, id);
        assertOpen(c);
        if (r == null || !hasText(r.reason())) {                                                                      // [V8]
            throw new Exceptions("error.visitor.archiveReasonRequired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String reason = catalogCode(c.orgId(), "VISITOR_ARCHIVE_REASON", r.reason(), "motivo");
        if (reason == null) {
            throw new Exceptions("error.visitor.archiveReasonRequired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        jdbc.update("update visitor_case set stage = 'ARCHIVED', archive_reason = :r, closed_at = :at, next_action_date = null, updated_at = :at, updated_by = :by,"
                        + " version = version + 1 where id = :id",
                new MapSqlParameterSource("r", reason).addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", id));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("from", c.stage());
        d.put("reason", reason);
        audit.record(new AuditService.Command(MODULE, "ARCHIVE", ENTITY, id, c.orgId(), c.branchId(), d));
        return toResponse(scope, load(scope, id));
    }

    /** Borra un caso registrado por error: solo NEW y sin contactos. La persona se conserva. */
    @Transactional
    public void delete(AuthenticatedActor actor, AccessScope scope, UUID id) {
        Row c = load(scope, id);
        Integer contacts = jdbc.queryForObject("select count(*) from follow_up_contact where subject_type = 'VISITOR_CASE' and subject_id = :id",
                new MapSqlParameterSource("id", id), Integer.class);
        if (!"NEW".equals(c.stage()) || (contacts != null && contacts > 0)) {
            throw new Exceptions("error.visitor.deleteNotAllowed", HttpStatus.CONFLICT);
        }
        jdbc.update("delete from visitor_case where id = :id", new MapSqlParameterSource("id", id));
        audit.record(new AuditService.Command(MODULE, "DELETE", ENTITY, id, c.orgId(), c.branchId(), Map.of("person", personName(c.personId()))));
    }

    // ---------------------------------------------------------------- embudo

    @Transactional(readOnly = true)
    public VisitorDtos.Funnel funnel(AccessScope scope, VisitorDtos.FunnelRequest r) {
        LocalDate today = LocalDate.now(clock);
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(visible(scope, ps, "c"));
        ps.addValue("today", java.sql.Date.valueOf(today)).addValue("now", Timestamp.from(clock.instant()));
        if (r != null && r.branchId() != null) {
            w.append(" and c.branch_id = :fb");
            ps.addValue("fb", r.branchId());
        }
        if (r != null && r.from() != null) {
            w.append(" and c.first_visit_date >= :from");
            ps.addValue("from", java.sql.Date.valueOf(r.from()));
        }
        if (r != null && r.to() != null) {
            w.append(" and c.first_visit_date <= :to");
            ps.addValue("to", java.sql.Date.valueOf(r.to()));
        }
        List<VisitorDtos.FunnelBranch> rows = jdbc.query("select c.branch_id, b.name,"
                + " count(*) filter (where c.stage = 'NEW') as n_new,"
                + " count(*) filter (where c.stage = 'IN_FOLLOWUP') as n_fu,"
                + " count(*) filter (where c.stage = 'INTEGRATED') as n_int,"
                + " count(*) filter (where c.stage = 'CONVERTED') as n_conv,"
                + " count(*) filter (where c.stage = 'ARCHIVED') as n_arch,"
                + " count(*) as n_total,"
                + " avg(extract(epoch from (c.first_contact_at - c.created_at)) / 3600.0) as avg_h,"
                + " count(*) filter (where (c.stage in ('NEW','IN_FOLLOWUP','INTEGRATED') and c.next_action_date < :today)"
                + "   or (c.stage = 'NEW' and c.first_contact_at is null and c.created_at < cast(:now as timestamptz) - make_interval(hours => coalesce(r.new_sla_hours, 48)))) as n_over"
                + " from visitor_case c join branch b on b.id = c.branch_id left join visitor_rules r on r.organization_id = c.organization_id"
                + " where " + w + " group by c.branch_id, b.name order by lower(b.name)", ps, (rs, i) -> funnelRow(
                (UUID) rs.getObject(1), rs.getString(2), rs.getLong("n_new"), rs.getLong("n_fu"), rs.getLong("n_int"), rs.getLong("n_conv"),
                rs.getLong("n_arch"), rs.getLong("n_total"), rs.getObject("avg_h") == null ? null : rs.getDouble("avg_h"), rs.getLong("n_over")));
        long nn = 0, nf = 0, ni = 0, nc = 0, na = 0, nt = 0, no = 0;
        double sumH = 0;
        long withH = 0;
        for (VisitorDtos.FunnelBranch b : rows) {
            nn += b.newCases();
            nf += b.inFollowup();
            ni += b.integrated();
            nc += b.converted();
            na += b.archived();
            nt += b.total();
            no += b.overdue();
            if (b.avgFirstContactHours() != null) {
                sumH += b.avgFirstContactHours() * b.total();
                withH += b.total();
            }
        }
        VisitorDtos.FunnelBranch totals = funnelRow(null, null, nn, nf, ni, nc, na, nt, withH == 0 ? null : sumH / withH, no);
        return new VisitorDtos.Funnel(rows, totals);
    }

    private static VisitorDtos.FunnelBranch funnelRow(UUID branchId, String name, long n, long f, long i, long c, long a, long t, Double avgH, long over) {
        Double integration = t == 0 ? null : (i + c) * 1.0 / t;
        Double conversion = t == 0 ? null : c * 1.0 / t;
        Double avg = avgH == null ? null : Math.round(avgH * 10.0) / 10.0;
        return new VisitorDtos.FunnelBranch(branchId, name, n, f, i, c, a, t, integration, conversion, avg, over);
    }

    // ---------------------------------------------------------------- alertas de plazo

    /** NEW sin contacto pasado el plazo de la organización: avisa una sola vez al consolidador y a los administradores de la sede. */
    @Transactional
    public int alertOverdue(UUID onlyOrg) {
        MapSqlParameterSource ps = new MapSqlParameterSource("now", Timestamp.from(clock.instant()));
        String org = "";
        if (onlyOrg != null) {
            org = " and c.organization_id = :org";
            ps.addValue("org", onlyOrg);
        }
        record Due(UUID id, UUID org, UUID branch, UUID person, UUID consolidator, int hours) {
        }
        List<Due> due = jdbc.query("select c.id, c.organization_id, c.branch_id, c.person_id, c.consolidator_id, coalesce(r.new_sla_hours, 48) as h"
                + " from visitor_case c left join visitor_rules r on r.organization_id = c.organization_id"
                + " where c.stage = 'NEW' and c.first_contact_at is null and c.sla_alerted_at is null"
                + " and c.created_at < cast(:now as timestamptz) - make_interval(hours => coalesce(r.new_sla_hours, 48))" + org, ps,
                (rs, i) -> new Due((UUID) rs.getObject(1), (UUID) rs.getObject(2), (UUID) rs.getObject(3), (UUID) rs.getObject(4), (UUID) rs.getObject(5), rs.getInt(6)));
        int n = 0;
        for (Due d : due) {
            List<UUID> to = new ArrayList<>(notifications.branchAdmins(d.org(), d.branch()));
            if (d.consolidator() != null && !to.contains(d.consolidator())) {
                to.add(d.consolidator());
            }
            String name = personName(d.person());
            notifications.toPersons(NotificationType.VISITOR_SLA_BREACH, d.org(), to,
                    Map.of("name", name, "branch", branchName(d.branch()), "hours", String.valueOf(d.hours())), "/app/visitors/" + d.id(),
                    "visitor-sla:" + d.id());
            jdbc.update("update visitor_case set sla_alerted_at = :now where id = :id", new MapSqlParameterSource("now", Timestamp.from(clock.instant())).addValue("id", d.id()));
            n++;
        }
        return n;
    }

    // ---------------------------------------------------------------- consultas internas

    /** Alcance de filas: organización, sedes visibles y, para ORG_USER, solo los casos que tiene asignados (OWN). */
    static String visible(AccessScope scope, MapSqlParameterSource ps, String a) {
        StringBuilder sb = new StringBuilder(a + ".organization_id = :org");
        ps.addValue("org", scope.organizationId());
        if (!scope.allBranches()) {
            List<UUID> ids = scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
            ps.addValue("scopeBranches", ids);
            sb.append(" and ").append(a).append(".branch_id in (:scopeBranches)");
        }
        if (scope.role() == RoleType.ORG_USER) {
            ps.addValue("meOwn", scope.personId() == null ? new UUID(0, 0) : scope.personId());
            sb.append(" and ").append(a).append(".consolidator_id = :meOwn");
        }
        return sb.toString();
    }

    Row load(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String vis = visible(scope, ps, "c");
        return jdbc.query("select c.* from visitor_case c where c.id = :id and " + vis, ps, (rs, i) -> row(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private Row loadRaw(UUID id) {
        return jdbc.query("select c.* from visitor_case c where c.id = :id", new MapSqlParameterSource("id", id), (rs, i) -> row(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private static Row row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Row((UUID) rs.getObject("id"), (UUID) rs.getObject("organization_id"), (UUID) rs.getObject("branch_id"), (UUID) rs.getObject("person_id"),
                rs.getDate("first_visit_date").toLocalDate(), rs.getString("how_arrived"), (UUID) rs.getObject("invited_by"), rs.getString("stage"),
                (UUID) rs.getObject("consolidator_id"), instant(rs.getTimestamp("first_contact_at")), instant(rs.getTimestamp("last_contact_at")),
                rs.getDate("next_action_date") == null ? null : rs.getDate("next_action_date").toLocalDate(), instant(rs.getTimestamp("integrated_at")),
                instant(rs.getTimestamp("closed_at")), rs.getString("archive_reason"), rs.getString("notes"), rs.getString("source"),
                rs.getString("consent_status"), instant(rs.getTimestamp("consent_at")), instant(rs.getTimestamp("created_at")), rs.getLong("version"));
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static final String SUMMARY = "select c.id, c.person_id, p.first_name, p.last_name, p.phone, p.email, c.branch_id, b.name as branch_name, c.stage,"
            + " c.first_visit_date, c.how_arrived, c.consolidator_id, cp.first_name as c_first, cp.last_name as c_last, c.last_contact_at,"
            + " c.next_action_date, c.first_contact_at, c.source, c.created_at, coalesce(r.new_sla_hours, 48) as sla_hours";

    private VisitorDtos.Summary summary(java.sql.ResultSet rs, LocalDate today, Instant now) throws java.sql.SQLException {
        String stage = rs.getString("stage");
        java.sql.Date na = rs.getDate("next_action_date");
        LocalDate next = na == null ? null : na.toLocalDate();
        Instant created = instant(rs.getTimestamp("created_at"));
        boolean open = OPEN.contains(stage);
        boolean overdue = open && next != null && next.isBefore(today);
        boolean sla = "NEW".equals(stage) && rs.getTimestamp("first_contact_at") == null
                && created.plus(Duration.ofHours(rs.getInt("sla_hours"))).isBefore(now);
        String cf = rs.getString("c_first");
        return new VisitorDtos.Summary((UUID) rs.getObject("id"), (UUID) rs.getObject("person_id"),
                (rs.getString("first_name") + " " + rs.getString("last_name")).trim(), rs.getString("phone"), rs.getString("email"),
                (UUID) rs.getObject("branch_id"), rs.getString("branch_name"), stage, rs.getDate("first_visit_date").toLocalDate(),
                rs.getString("how_arrived"), (UUID) rs.getObject("consolidator_id"), cf == null ? null : (cf + " " + rs.getString("c_last")).trim(),
                instant(rs.getTimestamp("last_contact_at")), next, overdue, sla, rs.getString("source"), created);
    }

    private static String orderBy(List<SortRequest> sorts) {
        String key = sorts == null || sorts.isEmpty() ? "createdAt" : String.valueOf(sorts.get(0).getResolvedField());
        boolean desc = sorts == null || sorts.isEmpty() || "DESC".equalsIgnoreCase(sorts.get(0).getDirection());
        String dir = desc ? " desc" : " asc";
        String expr = switch (key) {
            case "name" -> "lower(p.last_name)" + dir + ", lower(p.first_name)" + dir;
            case "stage" -> "c.stage" + dir;
            case "branch" -> "lower(b.name)" + dir;
            case "firstVisit" -> "c.first_visit_date" + dir;
            case "lastContact" -> "c.last_contact_at" + dir + " nulls last";
            case "nextAction" -> "c.next_action_date" + dir + " nulls last";
            default -> "c.created_at" + dir;
        };
        return expr + ", c.id";
    }

    private VisitorDtos.Response toResponse(AccessScope scope, Row c) {
        return build(c);
    }

    private VisitorDtos.Response toResponseUnscoped(Row c) {
        return build(c);
    }

    private VisitorDtos.Response build(Row c) {
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        VisitorDtos.Summary s = jdbc.query(SUMMARY + " from visitor_case c join person p on p.id = c.person_id join branch b on b.id = c.branch_id"
                + " left join person cp on cp.id = c.consolidator_id left join visitor_rules r on r.organization_id = c.organization_id where c.id = :id",
                new MapSqlParameterSource("id", c.id()), (rs, i) -> summary(rs, today, now)).get(0);
        VisitorDtos.PersonRef person = jdbc.query("select id, first_name, last_name, doc_type, doc_number, phone, email, status, birth_date from person where id = :id",
                new MapSqlParameterSource("id", c.personId()), (rs, i) -> {
                    java.sql.Date bd = rs.getDate("birth_date");
                    return new VisitorDtos.PersonRef((UUID) rs.getObject("id"), (rs.getString("first_name") + " " + rs.getString("last_name")).trim(),
                            rs.getString("doc_type"), rs.getString("doc_number"), rs.getString("phone"), rs.getString("email"), rs.getString("status"),
                            bd == null ? null : Period.between(bd.toLocalDate(), today).getYears());
                }).get(0);
        List<VisitorDtos.Contact> contacts = jdbc.query("select f.id, f.at, f.method, f.result, f.notes, f.next_action_date, f.by_person_id, p.first_name, p.last_name"
                + " from follow_up_contact f left join person p on p.id = f.by_person_id where f.subject_type = 'VISITOR_CASE' and f.subject_id = :id order by f.at desc",
                new MapSqlParameterSource("id", c.id()), (rs, i) -> {
                    java.sql.Date na = rs.getDate("next_action_date");
                    String fn = rs.getString("first_name");
                    return new VisitorDtos.Contact((UUID) rs.getObject("id"), rs.getTimestamp("at").toInstant(), rs.getString("method"), rs.getString("result"),
                            rs.getString("notes"), na == null ? null : na.toLocalDate(), (UUID) rs.getObject("by_person_id"),
                            fn == null ? null : (fn + " " + rs.getString("last_name")).trim());
                });
        String invitedName = c.invitedBy() == null ? null : personName(c.invitedBy());
        boolean canConvert = "INTEGRATED".equals(c.stage()) && membership.getIfAvailable() != null;
        return new VisitorDtos.Response(s, person, c.invitedBy(), invitedName, c.notes(), c.archiveReason(), c.consentStatus(), c.consentAt(),
                c.firstContactAt(), c.integratedAt(), c.closedAt(), contacts, canConvert, c.version());
    }

    private List<VisitorDtos.Match> enrichContacts(List<VisitorDtos.Match> in) {
        List<VisitorDtos.Match> out = new ArrayList<>();
        for (VisitorDtos.Match m : in) {
            List<String[]> c = jdbc.query("select phone, email from person where id = :id", new MapSqlParameterSource("id", m.id()),
                    (rs, i) -> new String[]{rs.getString(1), rs.getString(2)});
            out.add(new VisitorDtos.Match(m.id(), m.fullName(), m.docType(), m.docNumber(), c.isEmpty() ? null : c.get(0)[0],
                    c.isEmpty() ? null : c.get(0)[1], m.status(), m.branchName(), m.openCaseId()));
        }
        return out;
    }

    // ---------------------------------------------------------------- reglas y utilidades

    private static void assertOpen(Row c) {
        if (!OPEN.contains(c.stage())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, c.stage());
        }
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
        Integer n = jdbc.queryForObject("select count(*) from person where id = :id and organization_id = :o",
                new MapSqlParameterSource("id", personId).addValue("o", orgId), Integer.class);
        if (n == null || n == 0) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
    }

    /** [V5] Consolidador: persona ACTIVA, adulta y de la sede (sede principal o acceso activo en ella). */
    void checkConsolidator(UUID orgId, UUID branchId, UUID personId) {
        List<Object[]> rows = jdbc.query("select p.status, p.birth_date, p.primary_branch_id,"
                        + " exists (select 1 from user_access ua where ua.person_id = p.id and ua.branch_id = :b and ua.status = 'ACTIVE') as has_access"
                        + " from person p where p.id = :id and p.organization_id = :o",
                new MapSqlParameterSource("id", personId).addValue("o", orgId).addValue("b", branchId),
                (rs, i) -> new Object[]{rs.getString(1), rs.getDate(2), rs.getObject(3), rs.getBoolean(4)});
        if (rows.isEmpty()) {
            throw new Exceptions("error.visitor.consolidatorInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Object[] r = rows.get(0);
        boolean active = "ACTIVE".equals(r[0]);
        boolean adult = r[1] == null || Period.between(((java.sql.Date) r[1]).toLocalDate(), LocalDate.now(clock)).getYears() >= ADULT_AGE;
        boolean ofBranch = branchId.equals(r[2]) || Boolean.TRUE.equals(r[3]);
        if (!active || !adult || !ofBranch) {
            throw new Exceptions("error.visitor.consolidatorInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /** Código de catálogo activo (base u organización); null si no viene; 400 si no existe. */
    String catalogCode(UUID orgId, String type, String raw, String label) {
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

    private void notifyAssigned(UUID orgId, UUID consolidator, UUID personId, String branch, UUID caseId) {
        notifications.toPersons(NotificationType.VISITOR_ASSIGNED, orgId, List.of(consolidator),
                Map.of("name", personName(personId), "branch", branch), "/app/visitors/" + caseId, null);
    }

    private static String safePhone(String p) {
        return p == null || p.isBlank() ? null : p;
    }

    static String clean(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        if (t.length() > max) {
            throw new Exceptions("error.common.tooLong", HttpStatus.BAD_REQUEST, "notas", max);
        }
        return t;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
