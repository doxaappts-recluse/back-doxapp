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
import pe.dcs.app.features.person.dto.HouseholdDtos;
import pe.dcs.app.features.person.service.HouseholdService;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M08 · Ritos: bautizo, matrimonio y presentación de niños comparten ciclo REQUESTED → APPROVED → SCHEDULED → COMPLETED (o CANCELLED).
 * [V5] un solo bautizo realizado por persona · [V6] la fecha programada no es pasada y la realizada no es futura · [V7] se completa con oficiante ·
 * [V8] el menor de edad necesita el consentimiento de un tutor · [V9] matrimonio: personas distintas, edad mínima, sin matrimonio vigente y cónyuge confirmado ·
 * [V10] requisitos (o excepción con motivo) · [V11] presentación: niño menor de la edad máxima con al menos un tutor adulto · [V13] lo realizado no se borra ·
 * [V15] una sola solicitud abierta por persona y tipo. El registro directo (acción A) anota un rito ya realizado sin pasar por aprobación.
 */
@Service
@RequiredArgsConstructor
public class RiteService {

    private static final Set<String> TYPES = Set.of("BAPTISM", "MARRIAGE", "DEDICATION");
    private static final Set<String> STATUSES = Set.of("REQUESTED", "APPROVED", "SCHEDULED", "COMPLETED", "CANCELLED");
    private static final Set<String> OPEN = Set.of("REQUESTED", "APPROVED", "SCHEDULED");
    private static final String ENTITY = "Rite";

    private final NamedParameterJdbcTemplate jdbc;
    private final RiteSupport support;
    private final RiteRulesService rules;
    private final RiteRequirementService requirements;
    private final AuthorizationService authz;
    private final ApprovalEngine engine;
    private final HouseholdService households;
    private final AuditService audit;
    private final Clock clock;

    private static final String SELECT = "select r.*, trim(p.first_name || ' ' || p.last_name) as person_name, p.birth_date as birth1, trim(p2.first_name || ' ' || p2.last_name) as person2_name,"
            + " p2.birth_date as birth2, b.name as branch_name, trim(o.first_name || ' ' || o.last_name) as officiant_name, trim(gc.first_name || ' ' || gc.last_name) as consent_name,"
            + " trim(rq.first_name || ' ' || rq.last_name) as req_name, c.id as cert_id, c.certificate_no as cert_no, c.status as cert_status"
            + " from rite r join person p on p.id = r.person_id left join person p2 on p2.id = r.person2_id join branch b on b.id = r.branch_id"
            + " left join person o on o.id = r.officiant_id left join person gc on gc.id = r.guardian_consent_by left join person rq on rq.id = r.requested_by"
            + " left join lateral (select id, certificate_no, status from certificate where rite_id = r.id order by (status = 'VALID') desc, issued_at desc limit 1) c on true";

    /** Fila cargada para operar. */
    record Row(UUID id, UUID orgId, UUID branchId, String type, UUID personId, UUID person2Id, String status, LocalDate date, String place, UUID officiantId, String officiantText,
               boolean external, String externalChurch, UUID consentBy, String civilNo, Instant spouse2At, UUID approvalId, String overrideReason, long version,
               LocalDate birth1, LocalDate birth2, String personName, String person2Name) {
        List<LocalDate> births() {
            List<LocalDate> l = new ArrayList<>();
            if (birth1 != null) {
                l.add(birth1);
            }
            if (birth2 != null) {
                l.add(birth2);
            }
            return l;
        }
    }

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public PageResponse<RiteDtos.RiteResponse> search(AccessScope scope, String type, RiteDtos.RiteSearch req) {
        String t = type(type);
        RiteDtos.RiteSearch.Filters f = req == null || req.filters() == null ? new RiteDtos.RiteSearch.Filters(null, null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource("t", t);
        StringBuilder w = new StringBuilder("r.rite_type = :t and ").append(RiteSupport.readable(RiteSupport.moduleOf(t), scope, ps, "r"));
        if (RiteSupport.hasText(f.q())) {
            ps.addValue("q", "%" + f.q().trim().toLowerCase().replace("%", "").replace("_", "") + "%");
            w.append(" and (lower(p.first_name || ' ' || p.last_name) like :q or lower(p.last_name || ' ' || p.first_name) like :q or lower(p.doc_number) like :q"
                    + " or lower(coalesce(p2.first_name || ' ' || p2.last_name, '')) like :q)");
        }
        if (f.branchId() != null) {
            w.append(" and r.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (RiteSupport.hasText(f.status())) {
            String st = f.status().trim().toUpperCase();
            if (!STATUSES.contains(st)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
            }
            w.append(" and r.status = :st");
            ps.addValue("st", st);
        }
        if (f.personId() != null) {
            w.append(" and (r.person_id = :fp or r.person2_id = :fp or exists (select 1 from rite_guardian g where g.rite_id = r.id and g.person_id = :fp))");
            ps.addValue("fp", f.personId());
        }
        Long total = jdbc.queryForObject("select count(*) from rite r join person p on p.id = r.person_id left join person p2 on p2.id = r.person2_id where " + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<RiteDtos.RiteResponse> rows = jdbc.query(SELECT + " where " + w
                + " order by (r.status in ('REQUESTED','APPROVED','SCHEDULED')) desc, coalesce(r.event_date, r.created_at::date) desc, r.created_at desc limit :lim offset :off", ps,
                (rs, i) -> toResponse(scope, rs));
        long tt = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) tt, (int) Math.ceil(tt / (double) size), size, page));
    }

    @Transactional(readOnly = true)
    public RiteDtos.RiteResponse get(AccessScope scope, String type, UUID id) {
        String t = type(type);
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id).addValue("t", t);
        String w = RiteSupport.readable(RiteSupport.moduleOf(t), scope, ps, "r");
        return jdbc.query(SELECT + " where r.id = :id and r.rite_type = :t and " + w, ps, (rs, i) -> toResponse(scope, rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    /** Ritos de una persona como protagonista (persona o cónyuge). */
    @Transactional(readOnly = true)
    public List<RiteDtos.RiteResponse> ofPerson(AccessScope scope, String type, UUID personId) {
        String t = type(type);
        MapSqlParameterSource ps = new MapSqlParameterSource("t", t).addValue("p", personId);
        String w = RiteSupport.readable(RiteSupport.moduleOf(t), scope, ps, "r");
        return jdbc.query(SELECT + " where r.rite_type = :t and (r.person_id = :p or r.person2_id = :p) and " + w
                + " order by coalesce(r.event_date, r.created_at::date) desc, r.created_at desc", ps, (rs, i) -> toResponse(scope, rs));
    }

    /** Presentaciones de niños donde la persona figura como tutora. */
    @Transactional(readOnly = true)
    public List<RiteDtos.RiteResponse> guardianOf(AccessScope scope, UUID personId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("p", personId);
        String w = RiteSupport.readable("CHILD_DEDICATION", scope, ps, "r");
        return jdbc.query(SELECT + " where r.rite_type = 'DEDICATION' and exists (select 1 from rite_guardian g where g.rite_id = r.id and g.person_id = :p) and " + w
                + " order by coalesce(r.event_date, r.created_at::date) desc", ps, (rs, i) -> toResponse(scope, rs));
    }

    private RiteDtos.RiteResponse toResponse(AccessScope scope, ResultSet rs) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        UUID branch = (UUID) rs.getObject("branch_id");
        String status = rs.getString("status");
        boolean pending = false;
        if ("REQUESTED".equals(status) && rs.getString("override_reason") == null) {
            pending = !requirements.checklist((UUID) rs.getObject("organization_id"), rs.getString("rite_type"), id, births(rs), support.today(branch), null).complete();
        }
        List<RiteDtos.PersonRef> guardians = jdbc.query("select p.id, trim(p.first_name || ' ' || p.last_name) from rite_guardian g join person p on p.id = g.person_id"
                + " where g.rite_id = :r order by 2", new MapSqlParameterSource("r", id), (g, i) -> new RiteDtos.PersonRef((UUID) g.getObject(1), g.getString(2)));
        Timestamp done = rs.getTimestamp("completed_at");
        Timestamp sp = rs.getTimestamp("spouse2_confirmed_at");
        return new RiteDtos.RiteResponse(id, rs.getString("rite_type"), (UUID) rs.getObject("person_id"), rs.getString("person_name"), (UUID) rs.getObject("person2_id"),
                rs.getString("person2_name"), branch, rs.getString("branch_name"), date(rs, "event_date"), rs.getString("place"), (UUID) rs.getObject("officiant_id"),
                rs.getString("officiant_name"), rs.getString("officiant_text"), rs.getBoolean("external"), rs.getString("external_church"), (UUID) rs.getObject("guardian_consent_by"),
                rs.getString("consent_name"), rs.getString("civil_record_no"), sp == null ? null : sp.toInstant(), status, (UUID) rs.getObject("approval_id"),
                rs.getString("override_reason"), rs.getString("origin"), rs.getString("req_name"), rs.getString("cancel_reason"), done == null ? null : done.toInstant(),
                rs.getString("certificate_no"), (UUID) rs.getObject("cert_id"), rs.getString("cert_status"), guardians, pending, !scope.canSeeBranch(branch),
                rs.getTimestamp("created_at").toInstant(), rs.getLong("version"));
    }

    private static List<LocalDate> births(ResultSet rs) throws SQLException {
        List<LocalDate> l = new ArrayList<>();
        if (rs.getDate("birth1") != null) {
            l.add(rs.getDate("birth1").toLocalDate());
        }
        if (rs.getDate("birth2") != null) {
            l.add(rs.getDate("birth2").toLocalDate());
        }
        return l;
    }

    private static LocalDate date(ResultSet rs, String col) throws SQLException {
        java.sql.Date d = rs.getDate(col);
        return d == null ? null : d.toLocalDate();
    }

    // ---------------------------------------------------------------- alta

    @Transactional
    public RiteDtos.RiteResponse create(AuthenticatedActor actor, AccessScope scope, String type, RiteDtos.RiteRequest r) {
        String t = type(type);
        String module = RiteSupport.moduleOf(t);
        authz.require(actor, module, Action.C);
        if (r == null || r.personId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "persona");
        }
        boolean record = "RECORD".equalsIgnoreCase(r.mode());
        if (r.mode() != null && !r.mode().isBlank() && !record && !"REQUEST".equalsIgnoreCase(r.mode())) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "modo");
        }
        if (record) {
            authz.require(actor, module, Action.A);
        }
        RiteSupport.PersonInfo p1 = support.activeVisible(scope, r.personId());
        UUID branch = support.branchFor(scope, actor.activeBranchId(), p1);
        LocalDate today = support.today(branch);
        RiteSupport.PersonInfo p2 = null;
        if (RiteSupport.MARRIAGE.equals(t)) {
            if (r.person2Id() == null) {
                throw new Exceptions("error.rite.spouseRequired", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (r.person2Id().equals(r.personId())) {
                throw new Exceptions("error.rite.spousesSame", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            p2 = support.activeVisible(scope, r.person2Id());
        } else if (r.person2Id() != null) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "persona 2");
        }
        LocalDate date = r.date();
        if (record) {
            if (date == null) {
                throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "fecha");
            }
            if (date.isAfter(today)) {
                throw new Exceptions("error.common.futureDate", HttpStatus.BAD_REQUEST);                                   // [V6]
            }
            if (!RiteSupport.hasText(r.officiantText()) && r.officiantId() == null) {
                throw new Exceptions("error.rite.officiantRequired", HttpStatus.UNPROCESSABLE_ENTITY);                       // [V7]
            }
        } else if (date != null && date.isBefore(today)) {
            throw new Exceptions("error.common.pastDate", HttpStatus.BAD_REQUEST);
        }
        if (r.officiantId() != null) {
            support.person(scope.organizationId(), r.officiantId());
        }
        boolean external = Boolean.TRUE.equals(r.external());
        Fields f = validate(scope, t, p1, p2, today, external, r.externalChurch(), r.guardianConsentBy(), r.guardianIds(), record);
        checkNoDuplicates(t, p1, p2);
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(clock.instant());
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("b", branch).addValue("t", t).addValue("p", p1.id())
                .addValue("p2", p2 == null ? null : p2.id()).addValue("d", date == null ? null : java.sql.Date.valueOf(date)).addValue("pl", RiteSupport.trim(r.place(), 120))
                .addValue("of", r.officiantId()).addValue("ot", RiteSupport.trim(r.officiantText(), 120)).addValue("ex", f.external).addValue("ec", f.church)
                .addValue("gc", f.consentBy).addValue("cn", RiteSupport.trim(r.civilRecordNo(), 40)).addValue("st", record ? "COMPLETED" : "REQUESTED")
                .addValue("orig", record ? "RECORD" : "REQUEST").addValue("rq", scope.personId()).addValue("at", now).addValue("by", actor.ownerId())
                .addValue("done", record ? now : null).addValue("sp", record && p2 != null ? now : null).addValue("spb", record && p2 != null ? scope.personId() : null);
        try {
            jdbc.update("insert into rite (id, organization_id, branch_id, rite_type, person_id, person2_id, event_date, place, officiant_id, officiant_text, external, external_church,"
                    + " guardian_consent_by, civil_record_no, status, origin, requested_by, completed_at, spouse2_confirmed_at, spouse2_confirmed_by, created_at, created_by)"
                    + " values (:id, :o, :b, :t, :p, :p2, :d, :pl, :of, :ot, :ex, :ec, :gc, :cn, :st, :orig, :rq, :done, :sp, :spb, :at, :by)", ps);
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions(RiteSupport.BAPTISM.equals(t) && record ? "error.rite.baptismExists" : "error.rite.duplicateOpen", HttpStatus.UNPROCESSABLE_ENTITY, p1.name());
        }
        for (UUID g : f.guardians) {
            jdbc.update("insert into rite_guardian (rite_id, person_id) values (:r, :p)", new MapSqlParameterSource("r", id).addValue("p", g));
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("type", t);
        d.put("person", p1.id().toString());
        if (p2 != null) {
            d.put("person2", p2.id().toString());
        }
        d.put("mode", record ? "RECORD" : "REQUEST");
        if (record) {
            afterCompleted(actor, scope, t, id, p1, p2);
        } else {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("riteId", id.toString());
            payload.put("riteType", t);
            payload.put("personName", p1.name());
            if (p2 != null) {
                payload.put("person2Name", p2.name());
            }
            payload.put("branchName", support.branchName(branch));
            if (date != null) {
                payload.put("effectiveDate", date.toString());
            }
            UUID approval = engine.open(new ApprovalEngine.NewRequest(scope.organizationId(), branch, null, "RITE_" + t, "PERSON", p1.id(), scope.personId(), null, payload));
            jdbc.update("update rite set approval_id = :a where id = :id", new MapSqlParameterSource("a", approval).addValue("id", id));
        }
        audit.record(new AuditService.Command(module, record ? "RECORD" : "REQUEST", ENTITY, id, scope.organizationId(), branch, d));
        return get(scope, t, id);
    }

    /** Datos validados de un rito. */
    private record Fields(boolean external, String church, UUID consentBy, List<UUID> guardians) {
    }

    /** Reglas propias de cada tipo [V5, V8, V9, V11]. En el registro directo (recorded) no se exige el estado civil vigente del cónyuge ya casado por error de carga. */
    private Fields validate(AccessScope scope, String t, RiteSupport.PersonInfo p1, RiteSupport.PersonInfo p2, LocalDate today, boolean external, String churchRaw, UUID consentRaw,
                            List<UUID> guardiansRaw, boolean recorded) {
        String church = RiteSupport.trim(churchRaw, 120);
        UUID consent = null;
        List<UUID> guardians = new ArrayList<>();
        switch (t) {
            case "BAPTISM" -> {
                if (external && church == null) {
                    throw new Exceptions("error.rite.externalChurchRequired", HttpStatus.UNPROCESSABLE_ENTITY);
                }
                if (!external) {
                    church = null;
                }
                Integer done = jdbc.queryForObject("select count(*) from rite where rite_type = 'BAPTISM' and person_id = :p and status = 'COMPLETED'",
                        new MapSqlParameterSource("p", p1.id()), Integer.class);
                if (done != null && done > 0) {
                    throw new Exceptions("error.rite.baptismExists", HttpStatus.UNPROCESSABLE_ENTITY, p1.name());          // [V5]
                }
                if (RiteSupport.minor(p1.birthDate(), today)) {
                    consent = consentRaw;
                    if (consent == null) {
                        throw new Exceptions("error.rite.guardianConsentRequired", HttpStatus.UNPROCESSABLE_ENTITY);          // [V8]
                    }
                    if (!support.householdGuardians(p1.id()).contains(consent)) {
                        throw new Exceptions("error.rite.guardianConsentInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
                    }
                }
            }
            case "MARRIAGE" -> {
                external = false;
                church = null;
                int min = rules.get(scope.organizationId()).marriageMinAge();
                for (RiteSupport.PersonInfo p : List.of(p1, p2)) {
                    Integer age = RiteSupport.age(p.birthDate(), today);
                    if (age != null && age < min) {
                        throw new Exceptions("error.rite.minAge", HttpStatus.UNPROCESSABLE_ENTITY, p.name(), min);           // [V9]
                    }
                    if ("MARRIED".equals(p.maritalStatus()) && !recorded) {
                        throw new Exceptions("error.rite.alreadyMarried", HttpStatus.UNPROCESSABLE_ENTITY, p.name());
                    }
                }
            }
            case "DEDICATION" -> {
                external = false;
                church = null;
                int max = rules.get(scope.organizationId()).dedicationMaxAge();
                Integer age = RiteSupport.age(p1.birthDate(), today);
                if (age == null) {
                    throw new Exceptions("error.rite.birthDateRequired", HttpStatus.UNPROCESSABLE_ENTITY, p1.name());
                }
                if (age >= max) {
                    throw new Exceptions("error.rite.dedicationAge", HttpStatus.UNPROCESSABLE_ENTITY, p1.name(), max);     // [V11]
                }
                Set<UUID> ids = new LinkedHashSet<>(guardiansRaw == null ? List.of() : guardiansRaw);
                if (ids.isEmpty()) {
                    ids.addAll(support.householdGuardians(p1.id()));
                }
                ids.remove(p1.id());
                boolean adult = false;
                for (UUID g : ids) {
                    RiteSupport.PersonInfo gp = support.person(scope.organizationId(), g);
                    adult |= !RiteSupport.minor(gp.birthDate(), today);
                }
                if (ids.isEmpty() || !adult) {
                    throw new Exceptions("error.rite.guardianRequired", HttpStatus.UNPROCESSABLE_ENTITY);
                }
                guardians.addAll(ids);
            }
            default -> throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo");
        }
        return new Fields(external, church, consent, guardians);
    }

    private void checkNoDuplicates(String t, RiteSupport.PersonInfo p1, RiteSupport.PersonInfo p2) {
        MapSqlParameterSource ps = new MapSqlParameterSource("t", t).addValue("a", p1.id()).addValue("b", p2 == null ? p1.id() : p2.id());
        Integer n = jdbc.queryForObject("select count(*) from rite where rite_type = :t and status in ('REQUESTED','APPROVED','SCHEDULED')"
                + " and (person_id in (:a, :b) or person2_id in (:a, :b))", ps, Integer.class);
        if (n != null && n > 0) {
            throw new Exceptions("error.rite.duplicateOpen", HttpStatus.UNPROCESSABLE_ENTITY, p1.name());                    // [V15]
        }
    }

    // ---------------------------------------------------------------- edición

    @Transactional
    public RiteDtos.RiteResponse update(AuthenticatedActor actor, AccessScope scope, String type, UUID id, RiteDtos.RiteUpdate r) {
        String t = type(type);
        authz.require(actor, RiteSupport.moduleOf(t), Action.E);
        Row row = lock(scope, t, id);
        if (r == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "datos");
        }
        openOrThrow(row);
        RiteSupport.PersonInfo p1 = support.person(row.orgId(), row.personId());
        RiteSupport.PersonInfo p2 = row.person2Id() == null ? null : support.person(row.orgId(), row.person2Id());
        LocalDate today = support.today(row.branchId());
        boolean external = r.external() == null ? row.external() : r.external();
        String church = r.externalChurch() == null ? row.externalChurch() : r.externalChurch();
        UUID consentIn = r.guardianConsentBy() == null ? row.consentBy() : r.guardianConsentBy();
        List<UUID> guardiansIn = r.guardianIds() == null ? currentGuardians(id) : r.guardianIds();
        Fields f = validateForUpdate(scope, t, p1, p2, today, external, church, consentIn, guardiansIn);
        if (r.officiantId() != null) {
            support.person(scope.organizationId(), r.officiantId());
        }
        UUID officiant = r.officiantId() != null ? r.officiantId() : (RiteSupport.hasText(r.officiantText()) ? null : row.officiantId());
        String officiantText = RiteSupport.hasText(r.officiantText()) ? RiteSupport.trim(r.officiantText(), 120) : (r.officiantId() != null ? null : row.officiantText());
        int n = jdbc.update("update rite set officiant_id = :of, officiant_text = :ot, external = :ex, external_church = :ec, guardian_consent_by = :gc, civil_record_no = :cn, place = :pl,"
                        + " updated_at = :at, updated_by = :by, version = version + 1 where id = :id and (:v is null or version = :v)",
                new MapSqlParameterSource("of", officiant).addValue("ot", officiantText).addValue("ex", f.external).addValue("ec", f.church).addValue("gc", f.consentBy)
                        .addValue("cn", r.civilRecordNo() == null ? null : RiteSupport.trim(r.civilRecordNo(), 40)).addValue("pl", r.place() == null ? row.place() : RiteSupport.trim(r.place(), 120))
                        .addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", id).addValue("v", r.version()));
        if (n == 0) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        if (RiteSupport.DEDICATION.equals(t)) {
            jdbc.update("delete from rite_guardian where rite_id = :r", new MapSqlParameterSource("r", id));
            for (UUID g : f.guardians) {
                jdbc.update("insert into rite_guardian (rite_id, person_id) values (:r, :p)", new MapSqlParameterSource("r", id).addValue("p", g));
            }
        }
        audit.record(new AuditService.Command(RiteSupport.moduleOf(t), "UPDATE", ENTITY, id, scope.organizationId(), row.branchId(), Map.of("type", t)));
        return get(scope, t, id);
    }

    /** Como validate() pero sin repetir las comprobaciones de estado civil ni de bautizo ya realizadas al crear. */
    private Fields validateForUpdate(AccessScope scope, String t, RiteSupport.PersonInfo p1, RiteSupport.PersonInfo p2, LocalDate today, boolean external, String church,
                                     UUID consent, List<UUID> guardians) {
        if (RiteSupport.BAPTISM.equals(t)) {
            if (external && !RiteSupport.hasText(church)) {
                throw new Exceptions("error.rite.externalChurchRequired", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            UUID c = null;
            if (RiteSupport.minor(p1.birthDate(), today)) {
                c = consent;
                if (c == null) {
                    throw new Exceptions("error.rite.guardianConsentRequired", HttpStatus.UNPROCESSABLE_ENTITY);
                }
                if (!support.householdGuardians(p1.id()).contains(c)) {
                    throw new Exceptions("error.rite.guardianConsentInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
                }
            }
            return new Fields(external, external ? RiteSupport.trim(church, 120) : null, c, List.of());
        }
        if (RiteSupport.MARRIAGE.equals(t)) {
            return new Fields(false, null, null, List.of());
        }
        return validate(scope, t, p1, p2, today, false, null, null, guardians, true);
    }

    private List<UUID> currentGuardians(UUID riteId) {
        return jdbc.queryForList("select person_id from rite_guardian where rite_id = :r", new MapSqlParameterSource("r", riteId), UUID.class);
    }

    // ---------------------------------------------------------------- aprobación

    @Transactional
    public RiteDtos.RiteResponse approve(AuthenticatedActor actor, AccessScope scope, String type, UUID id, String note) {
        String t = type(type);
        Row row = lock(scope, t, id);
        engine.approve(actor, scope, approvalOf(row), note);
        return get(scope, t, id);
    }

    @Transactional
    public RiteDtos.RiteResponse reject(AuthenticatedActor actor, AccessScope scope, String type, UUID id, String reason) {
        String t = type(type);
        Row row = lock(scope, t, id);
        engine.reject(actor, scope, approvalOf(row), reason);
        return get(scope, t, id);
    }

    private UUID approvalOf(Row row) {
        if (row.approvalId() == null || !"REQUESTED".equals(row.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, row.status());
        }
        return row.approvalId();
    }

    /** Lo llama el handler al aprobarse: comprueba los requisitos [V10] y deja el rito APPROVED. */
    @Transactional
    public void onApproved(ApprovalHandler.ApprovalRow request, UUID byPerson) {
        UUID id = UUID.fromString(String.valueOf(request.payload().get("riteId")));
        Row row = jdbc.query(SELECT + " where r.id = :id for update of r", new MapSqlParameterSource("id", id), (rs, i) -> row(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        if (!"REQUESTED".equals(row.status())) {
            throw new Exceptions("error.common.alreadyDecided", HttpStatus.CONFLICT);
        }
        RiteDtos.Checklist c = requirements.checklist(row.orgId(), row.type(), id, row.births(), support.today(row.branchId()), row.overrideReason());
        if (!c.complete() && !c.overridden()) {
            throw new Exceptions("error.rite.requirementsPending", HttpStatus.UNPROCESSABLE_ENTITY, String.join(", ", requirements.unmet(c)));
        }
        jdbc.update("update rite set status = 'APPROVED', updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", byPerson).addValue("id", id));
        audit.record(new AuditService.Command(RiteSupport.moduleOf(row.type()), "APPROVE", ENTITY, id, row.orgId(), row.branchId(), Map.of("type", row.type())));
    }

    /** Rechazo, cancelación del solicitante o cancelación del personal: el rito queda CANCELLED con el motivo. */
    @Transactional
    public void onClosed(ApprovalHandler.ApprovalRow request, String note) {
        UUID id = UUID.fromString(String.valueOf(request.payload().get("riteId")));
        markCancelled(id, note == null ? "-" : note);
    }

    private void markCancelled(UUID id, String reason) {
        jdbc.update("update rite set status = 'CANCELLED', cancel_reason = coalesce(cancel_reason, :r), updated_at = :at, version = version + 1 where id = :id and status in ('REQUESTED','APPROVED','SCHEDULED')",
                new MapSqlParameterSource("r", RiteSupport.trim(reason, 300)).addValue("at", Timestamp.from(clock.instant())).addValue("id", id));
    }

    // ---------------------------------------------------------------- requisitos y excepción

    @Transactional(readOnly = true)
    public RiteDtos.Checklist checklist(AccessScope scope, String type, UUID id) {
        String t = type(type);
        get(scope, t, id);
        Row row = jdbc.query(SELECT + " where r.id = :id", new MapSqlParameterSource("id", id), (rs, i) -> row(rs)).get(0);
        return requirements.checklist(row.orgId(), t, id, row.births(), support.today(row.branchId()), row.overrideReason());
    }

    @Transactional
    public RiteDtos.Checklist mark(AuthenticatedActor actor, AccessScope scope, String type, UUID id, UUID requirementId, RiteDtos.CheckRequest r) {
        String t = type(type);
        authz.require(actor, RiteSupport.moduleOf(t), Action.E);
        Row row = lock(scope, t, id);
        if (!"REQUESTED".equals(row.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, row.status());
        }
        requirements.mark(actor, scope, t, id, requirementId, r);
        return checklist(scope, t, id);
    }

    @Transactional
    public RiteDtos.RiteResponse override(AuthenticatedActor actor, AccessScope scope, String type, UUID id, RiteDtos.OverrideRequest r) {
        String t = type(type);
        authz.require(actor, RiteSupport.moduleOf(t), Action.O);
        Row row = lock(scope, t, id);
        String reason = RiteSupport.trim(r == null ? null : r.reason(), 300);
        if (reason == null || reason.length() < 5) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "motivo");
        }
        if (!"REQUESTED".equals(row.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, row.status());
        }
        jdbc.update("update rite set override_reason = :r, override_by = :by, updated_at = :at, version = version + 1 where id = :id",
                new MapSqlParameterSource("r", reason).addValue("by", scope.personId()).addValue("at", Timestamp.from(clock.instant())).addValue("id", id));
        audit.record(new AuditService.Command(RiteSupport.moduleOf(t), "OVERRIDE", ENTITY, id, scope.organizationId(), row.branchId(), Map.of("reason", reason)));
        return get(scope, t, id);
    }

    // ---------------------------------------------------------------- ciclo

    @Transactional
    public RiteDtos.RiteResponse schedule(AuthenticatedActor actor, AccessScope scope, String type, UUID id, RiteDtos.ScheduleRequest r) {
        String t = type(type);
        authz.require(actor, RiteSupport.moduleOf(t), Action.E);
        Row row = lock(scope, t, id);
        if (r == null || r.date() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "fecha");
        }
        if (!"APPROVED".equals(row.status()) && !"SCHEDULED".equals(row.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, row.status());
        }
        if (r.date().isBefore(support.today(row.branchId()))) {
            throw new Exceptions("error.common.pastDate", HttpStatus.BAD_REQUEST);                                            // [V6]
        }
        if (r.officiantId() != null) {
            support.person(scope.organizationId(), r.officiantId());
        }
        UUID officiant = r.officiantId() != null ? r.officiantId() : (RiteSupport.hasText(r.officiantText()) ? null : row.officiantId());
        String officiantText = RiteSupport.hasText(r.officiantText()) ? RiteSupport.trim(r.officiantText(), 120) : (r.officiantId() != null ? null : row.officiantText());
        jdbc.update("update rite set status = 'SCHEDULED', event_date = :d, place = coalesce(:pl, place), officiant_id = :of, officiant_text = :ot, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("d", java.sql.Date.valueOf(r.date())).addValue("pl", RiteSupport.trim(r.place(), 120)).addValue("of", officiant).addValue("ot", officiantText)
                        .addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", id));
        audit.record(new AuditService.Command(RiteSupport.moduleOf(t), "SCHEDULE", ENTITY, id, scope.organizationId(), row.branchId(), Map.of("date", r.date().toString())));
        return get(scope, t, id);
    }

    /** Registra la realización del rito [V6, V7]; el matrimonio pide al cónyuge confirmado [V9] y actualiza el estado civil y el hogar. */
    @Transactional
    public RiteDtos.RiteResponse complete(AuthenticatedActor actor, AccessScope scope, String type, UUID id, RiteDtos.CompleteRequest r) {
        String t = type(type);
        authz.require(actor, RiteSupport.moduleOf(t), Action.E);
        Row row = lock(scope, t, id);
        if (!"APPROVED".equals(row.status()) && !"SCHEDULED".equals(row.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, row.status());
        }
        LocalDate date = r != null && r.date() != null ? r.date() : row.date();
        if (date == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "fecha");
        }
        if (date.isAfter(support.today(row.branchId()))) {
            throw new Exceptions("error.common.futureDate", HttpStatus.BAD_REQUEST);                                          // [V6]
        }
        UUID officiantId = r != null && r.officiantId() != null ? r.officiantId() : row.officiantId();
        String officiantText = r != null && RiteSupport.hasText(r.officiantText()) ? RiteSupport.trim(r.officiantText(), 120) : row.officiantText();
        if (r != null && r.officiantId() != null) {
            support.person(scope.organizationId(), r.officiantId());
            officiantText = RiteSupport.hasText(r.officiantText()) ? officiantText : null;
        }
        if (officiantId == null && officiantText == null) {
            throw new Exceptions("error.rite.officiantRequired", HttpStatus.UNPROCESSABLE_ENTITY);                              // [V7]
        }
        RiteSupport.PersonInfo p1 = support.person(row.orgId(), row.personId());
        RiteSupport.PersonInfo p2 = row.person2Id() == null ? null : support.person(row.orgId(), row.person2Id());
        if (!p1.active() || (p2 != null && !p2.active())) {
            throw new Exceptions("error.rite.personInactive", HttpStatus.UNPROCESSABLE_ENTITY, !p1.active() ? p1.name() : p2.name());
        }
        if (RiteSupport.MARRIAGE.equals(t)) {
            if (row.spouse2At() == null) {
                throw new Exceptions("error.rite.spouse2NotConfirmed", HttpStatus.UNPROCESSABLE_ENTITY);                          // [V9]
            }
            for (RiteSupport.PersonInfo p : List.of(p1, p2)) {
                if ("MARRIED".equals(p.maritalStatus())) {
                    throw new Exceptions("error.rite.alreadyMarried", HttpStatus.UNPROCESSABLE_ENTITY, p.name());
                }
            }
        }
        try {
            jdbc.update("update rite set status = 'COMPLETED', event_date = :d, place = coalesce(:pl, place), officiant_id = :of, officiant_text = :ot, completed_at = :at,"
                            + " updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                    new MapSqlParameterSource("d", java.sql.Date.valueOf(date)).addValue("pl", r == null ? null : RiteSupport.trim(r.place(), 120)).addValue("of", officiantId)
                            .addValue("ot", officiantText).addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", id));
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.rite.baptismExists", HttpStatus.UNPROCESSABLE_ENTITY, p1.name());
        }
        afterCompleted(actor, scope, t, id, p1, p2);
        audit.record(new AuditService.Command(RiteSupport.moduleOf(t), "COMPLETE", ENTITY, id, scope.organizationId(), row.branchId(), Map.of("date", date.toString())));
        return get(scope, t, id);
    }

    /** Efectos de un rito realizado: el matrimonio pone a ambos como casados y ordena su hogar. */
    private void afterCompleted(AuthenticatedActor actor, AccessScope scope, String t, UUID riteId, RiteSupport.PersonInfo p1, RiteSupport.PersonInfo p2) {
        if (!RiteSupport.MARRIAGE.equals(t) || p2 == null) {
            return;
        }
        Timestamp now = Timestamp.from(clock.instant());
        for (UUID p : List.of(p1.id(), p2.id())) {
            jdbc.update("update person set marital_status = 'MARRIED', updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                    new MapSqlParameterSource("at", now).addValue("by", actor.ownerId()).addValue("id", p));
        }
        String outcome;
        try {
            UUID h1 = activeHousehold(p1.id());
            UUID h2 = activeHousehold(p2.id());
            LocalDate today = LocalDate.now(clock);
            boolean a1 = !RiteSupport.minor(p1.birthDate(), today);
            boolean a2 = !RiteSupport.minor(p2.birthDate(), today);
            if (h1 == null && h2 == null) {
                households.create(actor, scope, new HouseholdDtos.Request("Familia " + lastName(p1.id()), null,
                        List.of(new HouseholdDtos.MemberRequest(p1.id(), "HEAD", a1), new HouseholdDtos.MemberRequest(p2.id(), "SPOUSE", a2)), null));
                outcome = "CREATED";
            } else if (h1 == null || h2 == null) {
                UUID hid = h1 != null ? h1 : h2;
                RiteSupport.PersonInfo other = h1 != null ? p2 : p1;
                households.addMember(actor, scope, hid, new HouseholdDtos.MemberRequest(other.id(), "SPOUSE", !RiteSupport.minor(other.birthDate(), today)));
                outcome = "JOINED";
            } else {
                outcome = "SKIPPED";
            }
        } catch (Exceptions e) {
            outcome = "SKIPPED";
        }
        audit.record(new AuditService.Command(RiteSupport.moduleOf(t), "MARRIAGE_EFFECTS", ENTITY, riteId, scope.organizationId(), null, Map.of("household", outcome)));
    }

    private UUID activeHousehold(UUID personId) {
        return jdbc.queryForList("select household_id from household_member where person_id = :p and left_at is null", new MapSqlParameterSource("p", personId), UUID.class)
                .stream().findFirst().orElse(null);
    }

    private String lastName(UUID personId) {
        List<String> n = jdbc.queryForList("select last_name from person where id = :id", new MapSqlParameterSource("id", personId), String.class);
        return n.isEmpty() ? "" : n.get(0);
    }

    /** El personal registra que el segundo cónyuge aceptó (el portal del miembro lo hará por su cuenta en M24). */
    @Transactional
    public RiteDtos.RiteResponse confirmSpouse(AuthenticatedActor actor, AccessScope scope, String type, UUID id) {
        String t = type(type);
        if (!RiteSupport.MARRIAGE.equals(t)) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        authz.require(actor, RiteSupport.moduleOf(t), Action.E);
        Row row = lock(scope, t, id);
        openOrThrow(row);
        if (row.spouse2At() == null) {
            jdbc.update("update rite set spouse2_confirmed_at = :at, spouse2_confirmed_by = :by, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                    new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", id));
            audit.record(new AuditService.Command(RiteSupport.moduleOf(t), "CONFIRM_SPOUSE", ENTITY, id, scope.organizationId(), row.branchId(), Map.of("type", t)));
        }
        return get(scope, t, id);
    }

    @Transactional
    public RiteDtos.RiteResponse cancel(AuthenticatedActor actor, AccessScope scope, String type, UUID id, String reasonText) {
        String t = type(type);
        authz.require(actor, RiteSupport.moduleOf(t), Action.E);
        Row row = lock(scope, t, id);
        String reason = RiteSupport.trim(reasonText, 300);
        if (reason == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "motivo");
        }
        openOrThrow(row);
        jdbc.update("update rite set cancel_reason = :r where id = :id", new MapSqlParameterSource("r", reason).addValue("id", id));
        if ("REQUESTED".equals(row.status()) && row.approvalId() != null) {
            engine.decideInternal(row.approvalId(), false, scope.personId(), reason);
        } else {
            markCancelled(id, reason);
        }
        audit.record(new AuditService.Command(RiteSupport.moduleOf(t), "CANCEL", ENTITY, id, scope.organizationId(), row.branchId(), Map.of("reason", reason)));
        return get(scope, t, id);
    }

    /** Solo se borra un rito cancelado [V13]; lo realizado se conserva (se anula el certificado, no el rito). */
    @Transactional
    public void delete(AuthenticatedActor actor, AccessScope scope, String type, UUID id) {
        String t = type(type);
        authz.require(actor, RiteSupport.moduleOf(t), Action.D);
        Row row = lock(scope, t, id);
        if ("COMPLETED".equals(row.status())) {
            throw new Exceptions("error.rite.cannotDeleteCompleted", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (!"CANCELLED".equals(row.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, row.status());
        }
        jdbc.update("delete from rite_requirement_check where subject_id = :id", new MapSqlParameterSource("id", id));
        jdbc.update("delete from rite where id = :id", new MapSqlParameterSource("id", id));
        audit.record(new AuditService.Command(RiteSupport.moduleOf(t), "DELETE", ENTITY, id, scope.organizationId(), row.branchId(), Map.of("type", t)));
    }

    // ---------------------------------------------------------------- utilidades

    private static void openOrThrow(Row row) {
        if (!OPEN.contains(row.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, row.status());
        }
    }

    private Row lock(AccessScope scope, String t, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id).addValue("t", t);
        String w = RiteSupport.writable(scope, ps, "r");
        List<Row> rows = jdbc.query(SELECT + " where r.id = :id and r.rite_type = :t and " + w + " for update of r", ps, (rs, i) -> row(rs));
        if (rows.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return rows.get(0);
    }

    private static Row row(ResultSet rs) throws SQLException {
        Timestamp sp = rs.getTimestamp("spouse2_confirmed_at");
        LocalDate b1 = date(rs, "birth1");
        LocalDate b2 = date(rs, "birth2");
        return new Row((UUID) rs.getObject("id"), (UUID) rs.getObject("organization_id"), (UUID) rs.getObject("branch_id"), rs.getString("rite_type"), (UUID) rs.getObject("person_id"),
                (UUID) rs.getObject("person2_id"), rs.getString("status"), date(rs, "event_date"), rs.getString("place"), (UUID) rs.getObject("officiant_id"), rs.getString("officiant_text"),
                rs.getBoolean("external"), rs.getString("external_church"), (UUID) rs.getObject("guardian_consent_by"), rs.getString("civil_record_no"), sp == null ? null : sp.toInstant(),
                (UUID) rs.getObject("approval_id"), rs.getString("override_reason"), rs.getLong("version"), b1, b2, rs.getString("person_name"), rs.getString("person2_name"));
    }

    static String type(String raw) {
        String t = raw == null ? "" : raw.trim().toUpperCase();
        if (!TYPES.contains(t)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo");
        }
        return t;
    }
}
