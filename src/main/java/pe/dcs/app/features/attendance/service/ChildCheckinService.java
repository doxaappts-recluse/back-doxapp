package pe.dcs.app.features.attendance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.attendance.dto.AttendanceDtos;
import pe.dcs.app.features.auth.service.SecretCipher;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M09 · Check-in seguro de niños. [V10] niño menor de {@code childMaxAge} con al menos un tutor autorizado (integrante de su hogar marcado como tutor en M06);
 * el niño recibe un código de 6 dígitos único por sesión y una etiqueta (con alergias solo con la acción H) [V11] la entrega exige el código o un tutor
 * autorizado con identidad verificada, y queda registrado quién recogió.
 */
@Service
@RequiredArgsConstructor
public class ChildCheckinService {

    private static final String MODULE = "CHILD_CHECKIN";
    private static final String ENTITY = "ChildCheckIn";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final NamedParameterJdbcTemplate jdbc;
    private final AttendanceService attendance;
    private final AttendanceRulesService rules;
    private final AttendanceSupport support;
    private final AuthorizationService authz;
    private final SecretCipher cipher;
    private final AuditService audit;
    private final Clock clock;

    // ---------------------------------------------------------------- consulta

    /** Sesiones abiertas de las sedes del usuario (para elegir en el kiosko). */
    @Transactional(readOnly = true)
    public List<AttendanceDtos.SessionSummary> openSessions(AccessScope scope) {
        MapSqlParameterSource ps = new MapSqlParameterSource();
        String w = AttendanceSupport.branchScope(scope, ps, "s");
        return jdbc.query("select s.id, s.branch_id, b.name as bn, s.context_type, s.context_id, s.title, s.session_date, s.starts_at, s.ends_at, s.status, s.anonymous_count, s.self_checkin,"
                        + " (select count(*) from child_checkin c where c.session_id = s.id) as children from attendance_session s join branch b on b.id = s.branch_id"
                        + " where " + w + " and s.status = 'OPEN' order by s.starts_at, b.name", ps,
                (rs, i) -> new AttendanceDtos.SessionSummary((UUID) rs.getObject("id"), (UUID) rs.getObject("branch_id"), rs.getString("bn"), rs.getString("context_type"),
                        (UUID) rs.getObject("context_id"), rs.getString("title"), rs.getDate("session_date").toLocalDate(), rs.getTimestamp("starts_at").toInstant(),
                        rs.getTimestamp("ends_at").toInstant(), rs.getString("status"), 0, 0, rs.getInt("anonymous_count"), 0, rs.getBoolean("self_checkin"), rs.getInt("children")));
    }

    /** Niños que se pueden registrar: sin texto los de la sede de la sesión; con texto, cualquiera de la organización. */
    @Transactional(readOnly = true)
    public List<AttendanceDtos.ChildCandidate> candidates(AccessScope scope, AttendanceDtos.ChildSearch req) {
        if (req == null || req.sessionId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sesión");
        }
        AttendanceService.Row s = attendance.load(scope, req.sessionId());
        int maxAge = rules.get(s.orgId()).childMaxAge();
        MapSqlParameterSource ps = new MapSqlParameterSource("o", s.orgId()).addValue("br", s.branchId()).addValue("sid", s.id())
                .addValue("minBirth", java.sql.Date.valueOf(LocalDate.now(clock).minusYears(maxAge)));
        StringBuilder w = new StringBuilder("p.organization_id = :o and p.status = 'ACTIVE' and p.anonymized_at is null and p.birth_date is not null and p.birth_date > :minBirth");
        String q = req.q() == null ? "" : req.q().trim().toLowerCase();
        if (q.isEmpty()) {
            w.append(" and p.primary_branch_id = :br");
        } else {
            String[] words = q.split("\\s+");
            for (int i = 0; i < words.length && i < 5; i++) {
                String k = "w" + i;
                String v = words[i].replace("%", "").replace("_", "");
                ps.addValue(k, "%" + v + "%").addValue(k + "d", v + "%");
                w.append(" and (lower(p.first_name || ' ' || p.last_name) like :").append(k).append(" or lower(coalesce(p.doc_number, '')) like :").append(k).append("d)");
            }
        }
        List<Object[]> kids = jdbc.query("select p.id, trim(p.first_name || ' ' || p.last_name), p.birth_date, b.name,"
                        + " exists (select 1 from child_checkin c where c.session_id = :sid and c.child_id = p.id)"
                        + " from person p left join branch b on b.id = p.primary_branch_id where " + w + " order by lower(p.first_name), lower(p.last_name) limit 30", ps,
                (rs, i) -> new Object[]{rs.getObject(1), rs.getString(2), rs.getDate(3).toLocalDate(), rs.getString(4), rs.getBoolean(5)});
        Map<UUID, List<AttendanceDtos.Guardian>> guardians = guardiansOf(kids.stream().map(k -> (UUID) k[0]).toList());
        List<AttendanceDtos.ChildCandidate> out = new ArrayList<>();
        LocalDate today = LocalDate.now(clock);
        for (Object[] k : kids) {
            out.add(new AttendanceDtos.ChildCandidate((UUID) k[0], (String) k[1], Period.between((LocalDate) k[2], today).getYears(), (String) k[3],
                    guardians.getOrDefault((UUID) k[0], List.of()), (boolean) k[4]));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<AttendanceDtos.CheckinResponse> list(AuthenticatedActor actor, AccessScope scope, UUID sessionId, boolean pendingOnly) {
        AttendanceService.Row s = attendance.load(scope, sessionId);
        boolean canH = canSeeAllergies(actor);
        return jdbc.query(SELECT + " where c.session_id = :sid" + (pendingOnly ? " and c.checked_out_at is null" : "") + " order by c.checked_in_at desc",
                new MapSqlParameterSource("sid", s.id()), (rs, i) -> map(rs, canH));
    }

    @Transactional(readOnly = true)
    public AttendanceDtos.CheckinResponse get(AuthenticatedActor actor, AccessScope scope, UUID id) {
        return map(loadRow(scope, id), canSeeAllergies(actor));
    }

    // ---------------------------------------------------------------- check-in / check-out

    @Transactional
    public AttendanceDtos.CheckinResponse checkin(AuthenticatedActor actor, AccessScope scope, AttendanceDtos.CheckinRequest r) {
        if (r == null || r.sessionId() == null || r.childId() == null || r.guardianId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "datos");
        }
        String room = AttendanceSupport.hasText(r.room()) ? r.room().trim() : null;
        if (room != null && room.length() > 60) {
            throw new Exceptions("error.common.tooLong", HttpStatus.BAD_REQUEST, "sala", 60);
        }
        AttendanceService.Row s = attendance.load(scope, r.sessionId());
        attendance.assertOpen(s);
        List<Object[]> child = jdbc.query("select birth_date, status, anonymized_at, allergies from person where id = :id and organization_id = :o",
                new MapSqlParameterSource("id", r.childId()).addValue("o", s.orgId()),
                (rs, i) -> new Object[]{rs.getDate(1), rs.getString(2), rs.getTimestamp(3), rs.getString(4)});
        if (child.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        Object[] c = child.get(0);
        if (!"ACTIVE".equals(c[1]) || c[2] != null) {
            throw new Exceptions("error.attendance.personInactive", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        int maxAge = rules.get(s.orgId()).childMaxAge();
        if (c[0] == null || Period.between(((java.sql.Date) c[0]).toLocalDate(), LocalDate.now(clock)).getYears() >= maxAge) {
            throw new Exceptions("error.checkin.notChild", HttpStatus.UNPROCESSABLE_ENTITY, maxAge);
        }
        List<AttendanceDtos.Guardian> guardians = guardiansOf(List.of(r.childId())).getOrDefault(r.childId(), List.of());
        if (guardians.isEmpty()) {                                                                                    // [V10]
            throw new Exceptions("error.checkin.noGuardian", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (guardians.stream().noneMatch(g -> g.id().equals(r.guardianId()))) {
            throw new Exceptions("error.checkin.guardianInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Integer already = jdbc.queryForObject("select count(*) from child_checkin where session_id = :s and child_id = :c",
                new MapSqlParameterSource("s", s.id()).addValue("c", r.childId()), Integer.class);
        if (already != null && already > 0) {
            throw new Exceptions("error.checkin.alreadyIn", HttpStatus.CONFLICT);
        }
        UUID id = UUID.randomUUID();
        String tag = null;
        for (int attempt = 0; attempt < 30 && tag == null; attempt++) {
            String candidate = String.format("%06d", RANDOM.nextInt(1_000_000));
            try {
                jdbc.update("insert into child_checkin (id, organization_id, branch_id, session_id, child_id, guardian_id, room, tag_code, allergy_snapshot, checked_in_at, checked_in_by)"
                                + " values (:id, :o, :b, :s, :c, :g, :r, :t, :al, :at, :by)",
                        new MapSqlParameterSource("id", id).addValue("o", s.orgId()).addValue("b", s.branchId()).addValue("s", s.id()).addValue("c", r.childId())
                                .addValue("g", r.guardianId()).addValue("r", room).addValue("t", candidate).addValue("al", c[3]).addValue("at", Timestamp.from(clock.instant()))
                                .addValue("by", actor.ownerId()));
                tag = candidate;
            } catch (DuplicateKeyException e) {
                if (e.getMessage() != null && e.getMessage().contains("ux_cc_child")) {
                    throw new Exceptions("error.checkin.alreadyIn", HttpStatus.CONFLICT);
                }
            }
        }
        if (tag == null) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, "tag");
        }
        attendance.recordInternal(s, r.childId(), "PRESENT", "LIST", actor.ownerId());                                // el niño también cuenta como asistente
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("child", r.childId().toString());
        d.put("guardian", r.guardianId().toString());
        d.put("room", room);
        audit.record(new AuditService.Command(MODULE, "CHECKIN", ENTITY, id, s.orgId(), s.branchId(), d));
        return get(actor, scope, id);
    }

    @Transactional
    public AttendanceDtos.CheckinResponse checkout(AuthenticatedActor actor, AccessScope scope, UUID id, AttendanceDtos.CheckoutRequest req) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String w = AttendanceSupport.branchScope(scope, ps, "c");
        List<Object[]> cur = jdbc.query("select c.child_id, c.guardian_id, c.tag_code, c.checked_out_at, c.organization_id, c.branch_id from child_checkin c where c.id = :id and " + w, ps,
                (rs, i) -> new Object[]{rs.getObject(1), rs.getObject(2), rs.getString(3), rs.getTimestamp(4), rs.getObject(5), rs.getObject(6)});
        if (cur.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        Object[] c = cur.get(0);
        if (c[3] != null) {
            throw new Exceptions("error.checkin.alreadyOut", HttpStatus.CONFLICT);
        }
        UUID childId = (UUID) c[0];
        UUID pickedUp;
        String method;
        if (req != null && AttendanceSupport.hasText(req.code())) {                                                    // [V11] con código
            if (!((String) c[2]).equals(req.code().trim())) {
                throw new Exceptions("error.checkin.tagMismatch", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            pickedUp = req.guardianId() != null && authorized(childId, req.guardianId(), (UUID) c[1]) ? req.guardianId() : (UUID) c[1];
            method = "CODE";
        } else if (req != null && req.guardianId() != null && Boolean.TRUE.equals(req.verified()) && authorized(childId, req.guardianId(), (UUID) c[1])) {   // tutor verificado
            pickedUp = req.guardianId();
            method = "GUARDIAN";
        } else {
            throw new Exceptions("error.checkin.tagMismatch", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        int n = jdbc.update("update child_checkin set checked_out_at = :at, checked_out_by = :by, picked_up_by = :pu, pickup_method = :pm where id = :id and checked_out_at is null",
                new MapSqlParameterSource("id", id).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()).addValue("pu", pickedUp).addValue("pm", method));
        if (n == 0) {
            throw new Exceptions("error.checkin.alreadyOut", HttpStatus.CONFLICT);
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("pickedUpBy", pickedUp.toString());
        d.put("method", method);
        audit.record(new AuditService.Command(MODULE, "CHECKOUT", ENTITY, id, (UUID) c[4], (UUID) c[5], d));
        return get(actor, scope, id);
    }

    // ---------------------------------------------------------------- internos

    private boolean authorized(UUID childId, UUID guardianId, UUID original) {
        return guardianId.equals(original) || guardiansOf(List.of(childId)).getOrDefault(childId, List.of()).stream().anyMatch(g -> g.id().equals(guardianId));
    }

    /** Tutores autorizados: integrantes activos del hogar del niño marcados como tutor (M06). */
    private Map<UUID, List<AttendanceDtos.Guardian>> guardiansOf(List<UUID> childIds) {
        Map<UUID, List<AttendanceDtos.Guardian>> out = new LinkedHashMap<>();
        if (childIds.isEmpty()) {
            return out;
        }
        jdbc.query("select cm.person_id as child, p.id, trim(p.first_name || ' ' || p.last_name) as name, p.phone"
                        + " from household_member cm join household_member gm on gm.household_id = cm.household_id and gm.left_at is null and gm.guardian and gm.person_id <> cm.person_id"
                        + " join person p on p.id = gm.person_id and p.status = 'ACTIVE' and p.anonymized_at is null"
                        + " where cm.person_id in (:ids) and cm.left_at is null order by lower(p.first_name)",
                new MapSqlParameterSource("ids", childIds), rs -> {
                    out.computeIfAbsent((UUID) rs.getObject("child"), k -> new ArrayList<>())
                            .add(new AttendanceDtos.Guardian((UUID) rs.getObject("id"), rs.getString("name"), rs.getString("phone")));
                });
        return out;
    }

    private boolean canSeeAllergies(AuthenticatedActor actor) {
        return authz.effectiveActions(actor, MODULE).contains("H");
    }

    private static final String SELECT = "select c.id, c.session_id, s.title, c.child_id, trim(ch.first_name || ' ' || ch.last_name) as child_name, ch.birth_date,"
            + " c.guardian_id, trim(g.first_name || ' ' || g.last_name) as guardian_name, c.room, c.tag_code, c.allergy_snapshot, c.checked_in_at, c.checked_out_at,"
            + " c.picked_up_by, trim(pu.first_name || ' ' || pu.last_name) as pu_name, c.pickup_method"
            + " from child_checkin c join attendance_session s on s.id = c.session_id join person ch on ch.id = c.child_id join person g on g.id = c.guardian_id"
            + " left join person pu on pu.id = c.picked_up_by";

    private record Raw(UUID id, UUID sessionId, String sessionTitle, UUID childId, String childName, LocalDate birth, UUID guardianId, String guardianName, String room,
                       String tag, String allergyCipher, java.time.Instant in, java.time.Instant out, UUID pickedUpBy, String pickedUpByName, String method) {
    }

    private static Raw raw(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp out = rs.getTimestamp("checked_out_at");
        java.sql.Date birth = rs.getDate("birth_date");
        return new Raw((UUID) rs.getObject("id"), (UUID) rs.getObject("session_id"), rs.getString("title"), (UUID) rs.getObject("child_id"), rs.getString("child_name"),
                birth == null ? null : birth.toLocalDate(), (UUID) rs.getObject("guardian_id"), rs.getString("guardian_name"), rs.getString("room"), rs.getString("tag_code"),
                rs.getString("allergy_snapshot"), rs.getTimestamp("checked_in_at").toInstant(), out == null ? null : out.toInstant(), (UUID) rs.getObject("picked_up_by"),
                rs.getString("pu_name"), rs.getString("pickup_method"));
    }

    private Raw loadRow(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String w = AttendanceSupport.branchScope(scope, ps, "c");
        return jdbc.query(SELECT + " where c.id = :id and " + w, ps, (rs, i) -> raw(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private AttendanceDtos.CheckinResponse map(java.sql.ResultSet rs, boolean canH) throws java.sql.SQLException {
        return build(raw(rs), canH);
    }

    private AttendanceDtos.CheckinResponse map(Raw r, boolean canH) {
        return build(r, canH);
    }

    /** La etiqueta lleva las alergias solo con la acción H (T-C5/T10). */
    private AttendanceDtos.CheckinResponse build(Raw r, boolean canH) {
        String allergies = null;
        if (canH && r.allergyCipher() != null && !r.allergyCipher().isBlank()) {
            try {
                allergies = cipher.decrypt(r.allergyCipher());
            } catch (RuntimeException e) {
                allergies = null;
            }
        }
        int age = r.birth() == null ? 0 : Period.between(r.birth(), LocalDate.now(clock)).getYears();
        return new AttendanceDtos.CheckinResponse(r.id(), r.sessionId(), r.sessionTitle(), r.childId(), r.childName(), age, r.guardianId(), r.guardianName(), r.room(), r.tag(),
                allergies, r.in(), r.out(), r.pickedUpBy(), r.pickedUpByName(), r.method());
    }
}
