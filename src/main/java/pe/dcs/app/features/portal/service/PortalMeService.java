package pe.dcs.app.features.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.approval.service.ApprovalEngine;
import pe.dcs.app.features.auth.service.SecretCipher;
import pe.dcs.app.features.event.service.EventSupport;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
import pe.dcs.app.features.portal.dto.PortalDtos.AttendanceHistoryRow;
import pe.dcs.app.features.portal.dto.PortalDtos.EventQuestionRow;
import pe.dcs.app.features.portal.dto.PortalDtos.HomeResponse;
import pe.dcs.app.features.portal.dto.PortalDtos.HouseholdMemberRow;
import pe.dcs.app.features.portal.dto.PortalDtos.MyDocumentRow;
import pe.dcs.app.features.portal.dto.PortalDtos.MembershipRow;
import pe.dcs.app.features.portal.dto.PortalDtos.MyFamilyResponse;
import pe.dcs.app.features.portal.dto.PortalDtos.MyEnrollmentRow;
import pe.dcs.app.features.portal.dto.PortalDtos.MyEmploymentRow;
import pe.dcs.app.features.portal.dto.PortalDtos.MyGivingRow;
import pe.dcs.app.features.portal.dto.PortalDtos.MyGroupRow;
import pe.dcs.app.features.portal.dto.PortalDtos.MyPastoralCaseRow;
import pe.dcs.app.features.portal.dto.PortalDtos.MyPayslipLineRow;
import pe.dcs.app.features.portal.dto.PortalDtos.MyPayslipRow;
import pe.dcs.app.features.portal.dto.PortalDtos.MyPrayerRequestRow;
import pe.dcs.app.features.portal.dto.PortalDtos.MyRegistrationRow;
import pe.dcs.app.features.portal.dto.PortalDtos.MyRequestRow;
import pe.dcs.app.features.portal.dto.PortalDtos.MyShiftRow;
import pe.dcs.app.features.portal.dto.PortalDtos.MySpaceReservationRow;
import pe.dcs.app.features.portal.dto.PortalDtos.MyWorkResponse;
import pe.dcs.app.features.portal.dto.PortalDtos.OpenClassRow;
import pe.dcs.app.features.portal.dto.PortalDtos.OpenEventRow;
import pe.dcs.app.features.portal.dto.PortalDtos.OpenGroupRow;
import pe.dcs.app.features.portal.dto.PortalDtos.OpenSpaceRow;
import pe.dcs.app.features.portal.dto.PortalDtos.OpenSpacesResponse;
import pe.dcs.app.features.portal.dto.PortalDtos.ProfileResponse;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.shared.storage.FileStorageService;
import pe.dcs.app.util.Exceptions;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M24 (cierre del spec) · secciones propias del portal resueltas SIN pasar por el nivel/contrato de otros módulos:
 * siempre acotadas al {@code personId} del token (nunca a un id de la URL — anti-IDOR [V13]). Mi perfil y Mis
 * solicitudes son las 2 secciones representativas de esta entrega (ver [D1] de V31); Comunicados ya funciona tal
 * cual con {@code /me/notifications} (M19) — no necesita código aquí.
 * <p>Mis documentos (seguimiento 2026-09-25, bloque {@code DOCUMENTS}): certificados propios ya emitidos por el
 * motor de plantillas de M18 desde M08 (ritos) o M13 (formación). No hay columna {@code person_id} en
 * {@code issued_document} (es genérico, por {@code subject_type}/{@code subject_id}), así que la pertenencia se
 * resuelve uniendo por el camino conocido de cada uno de esos dos módulos — el mismo candado se usa para listar y
 * para autorizar la descarga del PDF, nunca el {@code AccessScope} de nivel/branch del panel admin (ese solo
 * garantiza "algo de esta organización", no "esto es mío"). Si más módulos emiten documentos vía M18 en el futuro,
 * se agrega su propio camino aquí.</p>
 */
@Service
@RequiredArgsConstructor
public class PortalMeService {

    private static final String MY_DOCS_JOIN = """
            from issued_document d
            left join certificate c on c.issued_document_id = d.id left join rite r on r.id = c.rite_id
            left join training_certificate tc on tc.issued_document_id = d.id left join enrollment e on e.id = tc.enrollment_id
            """;

    private static final Set<String> PASTORAL_TYPES = Set.of("INACTIVE_FOLLOWUP", "VISIT", "COUNSELING", "CRISIS", "BEREAVEMENT", "HOSPITAL", "OTHER");
    private static final Set<String> PRAYER_VISIBILITY = Set.of("PRIVATE", "LEADERS", "CONGREGATION");
    private static final Set<String> OPEN_CLASS_STATUS = Set.of("PLANNED", "IN_PROGRESS");

    private final NamedParameterJdbcTemplate jdbc;
    private final PortalSettingsService settings;
    private final FileStorageService storage;
    private final ApprovalEngine approvals;
    private final NotificationService notifications;
    private final SecretCipher cipher;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    public ProfileResponse profile(AuthenticatedActor actor) {
        MapSqlParameterSource ps = new MapSqlParameterSource("o", actor.organizationId()).addValue("p", actor.ownerId());
        return jdbc.query("""
                select p.first_name, p.last_name, p.doc_type, p.doc_number, p.email, p.phone, p.birth_date, p.status, b.name as branch_name
                from person p left join branch b on b.id = p.primary_branch_id
                where p.organization_id = :o and p.id = :p
                """, ps, (rs, i) -> new ProfileResponse(rs.getString("first_name") + " " + rs.getString("last_name"), rs.getString("doc_type"),
                rs.getString("doc_number"), rs.getString("email"), rs.getString("phone"),
                rs.getDate("birth_date") == null ? null : rs.getDate("birth_date").toLocalDate(), rs.getString("branch_name"), rs.getString("status")))
                .stream().findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<MyRequestRow> myRequests(AuthenticatedActor actor) {
        return jdbc.query("""
                select id, type, status, reason, created_at, decided_at from approval_request
                where organization_id = :o and requested_by = :p order by created_at desc limit 100
                """, new MapSqlParameterSource("o", actor.organizationId()).addValue("p", actor.ownerId()), (rs, i) -> new MyRequestRow(
                (UUID) rs.getObject("id"), rs.getString("type"), rs.getString("status"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("decided_at") == null ? null : rs.getTimestamp("decided_at").toInstant(), rs.getString("reason")));
    }

    @Transactional(readOnly = true)
    public List<MyDocumentRow> myDocuments(AuthenticatedActor actor) {
        MapSqlParameterSource ps = new MapSqlParameterSource("o", actor.organizationId()).addValue("p", actor.ownerId());
        return jdbc.query("select d.id, d.type, d.document_no, d.status, d.issued_at " + MY_DOCS_JOIN
                + " where d.organization_id = :o and coalesce(r.person_id, e.person_id) = :p order by d.issued_at desc",
                ps, (rs, i) -> new MyDocumentRow((UUID) rs.getObject("id"), rs.getString("type"), rs.getString("document_no"),
                        rs.getString("status"), rs.getTimestamp("issued_at").toInstant()));
    }

    /** Mismo candado de {@link #myDocuments}: si la unión no encuentra al propio actor como dueño, 404 (nunca 403 — no delatar que el id existe). */
    @Transactional(readOnly = true)
    public FileStorageService.StoredFile myDocumentPdf(AuthenticatedActor actor, UUID documentId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("o", actor.organizationId()).addValue("p", actor.ownerId()).addValue("id", documentId);
        List<String> keys = jdbc.query("select d.pdf_ref " + MY_DOCS_JOIN
                + " where d.id = :id and d.organization_id = :o and coalesce(r.person_id, e.person_id) = :p",
                ps, (rs, i) -> rs.getString("pdf_ref"));
        String key = keys.isEmpty() ? null : keys.get(0);
        if (key == null) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return storage.get(key).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    /**
     * Bloque FAMILY (seguimiento 2026-09-25): hogar propio (M06), sin pasar por {@code PersonScope} —
     * mismo criterio que {@link #profile} — porque la visibilidad ahí es para el personal, no para autovisibilidad.
     * {@code allowChildrenView} [D5 de M24] es siempre de organización (nunca por sede): si está apagado, cualquier
     * integrante menor de edad distinto del propio actor se omite de la lista (nunca enmascarado a medias).
     */
    @Transactional(readOnly = true)
    public MyFamilyResponse family(AuthenticatedActor actor) {
        MapSqlParameterSource ps = new MapSqlParameterSource("o", actor.organizationId()).addValue("p", actor.ownerId());
        List<UUID> households = jdbc.query(
                "select household_id from household_member where person_id = :p and left_at is null",
                ps, (rs, i) -> (UUID) rs.getObject(1));
        UUID householdId = households.isEmpty() ? null : households.get(0);
        if (householdId == null) {
            return new MyFamilyResponse(null, List.of());
        }
        boolean allowChildren = settings.effectiveForMember(actor.organizationId(), actor.activeBranchId()).allowChildrenView();
        MapSqlParameterSource hp = new MapSqlParameterSource("o", actor.organizationId()).addValue("h", householdId).addValue("p", actor.ownerId());
        String name = jdbc.queryForObject("select name from household where id = :h and organization_id = :o", hp, String.class);
        List<HouseholdMemberRow> rows = jdbc.query("""
                select hm.person_id, p.first_name, p.last_name, hm.role, hm.guardian, p.birth_date
                from household_member hm join person p on p.id = hm.person_id
                where hm.household_id = :h and hm.left_at is null and p.organization_id = :o
                order by hm.role, p.first_name
                """, hp, (rs, i) -> {
            UUID personId = (UUID) rs.getObject("person_id");
            java.sql.Date bd = rs.getDate("birth_date");
            return new HouseholdMemberRow(personId, rs.getString("first_name") + " " + rs.getString("last_name"), rs.getString("role"),
                    rs.getBoolean("guardian"), personId.equals(actor.ownerId()), bd == null ? null : bd.toLocalDate());
        });
        List<HouseholdMemberRow> visible = rows.stream()
                .filter(r -> r.isSelf() || allowChildren || !isMinor(r.birthDate()))
                .toList();
        return new MyFamilyResponse(name, visible);
    }

    /** Bloque MEMBERSHIP (seguimiento 2026-09-26): historial propio de M08, nunca `exit_notes` (cifrado [H] — ni siquiera se lee la columna). */
    @Transactional(readOnly = true)
    public List<MembershipRow> membership(AuthenticatedActor actor) {
        MapSqlParameterSource ps = new MapSqlParameterSource("o", actor.organizationId()).addValue("p", actor.ownerId());
        return jdbc.query("""
                select m.id, b.name as branch_name, m.kind, m.status, m.current, m.start_date, m.end_date, m.exit_reason
                from membership m join branch b on b.id = m.branch_id
                where m.organization_id = :o and m.person_id = :p order by m.start_date desc nulls last, m.created_at desc
                """, ps, (rs, i) -> new MembershipRow((UUID) rs.getObject("id"), rs.getString("branch_name"), rs.getString("kind"),
                rs.getString("status"), rs.getBoolean("current"),
                rs.getDate("start_date") == null ? null : rs.getDate("start_date").toLocalDate(),
                rs.getDate("end_date") == null ? null : rs.getDate("end_date").toLocalDate(), rs.getString("exit_reason")));
    }

    /**
     * Bloque ATTENDANCE (seguimiento 2026-09-26): historial propio de M09 vía el índice existente {@code ix_ar_person}
     * (person_id, at desc) — ya cubre este filtro sin índice nuevo. El QR propio y el auto-registro de asistencia
     * ({@code /me/qr}, {@code /checkin/self}) ya son alcanzables hoy por cualquier actor autenticado gracias al
     * pseudo-módulo {@code MY_ACCOUNT} de {@code AdminAttendanceController} — no requieren código nuevo aquí, el
     * front del portal los llama directamente reutilizando ese mismo endpoint admin.
     */
    @Transactional(readOnly = true)
    public List<AttendanceHistoryRow> attendanceHistory(AuthenticatedActor actor) {
        MapSqlParameterSource ps = new MapSqlParameterSource("o", actor.organizationId()).addValue("p", actor.ownerId());
        return jdbc.query("""
                select ar.id, s.context_type, s.title, s.session_date, ar.status, ar.method, ar.at
                from attendance_record ar join attendance_session s on s.id = ar.session_id
                where ar.organization_id = :o and ar.person_id = :p order by ar.at desc limit 200
                """, ps, (rs, i) -> new AttendanceHistoryRow((UUID) rs.getObject("id"), rs.getString("context_type"), rs.getString("title"),
                rs.getDate("session_date").toLocalDate(), rs.getString("status"), rs.getString("method"), rs.getTimestamp("at").toInstant()));
    }

    /** Bloque GROUPS (seguimiento 2026-09-26): grupos propios (M10) vía {@code group_member}, sin candado de {@code AccessScope}. */
    @Transactional(readOnly = true)
    public List<MyGroupRow> myGroups(AuthenticatedActor actor) {
        MapSqlParameterSource ps = new MapSqlParameterSource("o", actor.organizationId()).addValue("p", actor.ownerId());
        return jdbc.query("""
                select g.id, g.name, g.category, g.audience, g.meeting_day, g.meeting_time, g.location, m.role, m.joined_at
                from group_member m join small_group g on g.id = m.group_id
                where m.organization_id = :o and m.person_id = :p and m.status = 'ACTIVE' order by g.name
                """, ps, (rs, i) -> {
            int day = rs.getInt("meeting_day");
            Integer meetingDay = rs.wasNull() ? null : day;
            java.sql.Time mt = rs.getTime("meeting_time");
            return new MyGroupRow((UUID) rs.getObject("id"), rs.getString("name"), rs.getString("category"), rs.getString("audience"), meetingDay,
                    mt == null ? null : mt.toLocalTime(), rs.getString("location"), rs.getString("role"), rs.getDate("joined_at").toLocalDate());
        });
    }

    /** Directorio de grupos abiertos ({@code open_to_join}) de la organización — cualquier sede, no solo la propia: es un
     * catálogo de autoservicio, no una pantalla de administración con alcance de sede. */
    @Transactional(readOnly = true)
    public List<OpenGroupRow> openGroups(AuthenticatedActor actor) {
        MapSqlParameterSource ps = new MapSqlParameterSource("o", actor.organizationId()).addValue("p", actor.ownerId());
        return jdbc.query("""
                select g.id, g.name, g.category, g.audience, b.name as branch_name, g.meeting_day, g.meeting_time, g.location, g.capacity,
                    (select count(*) from group_member c where c.group_id = g.id and c.status = 'ACTIVE') as active_count,
                    exists (select 1 from group_member me where me.group_id = g.id and me.person_id = :p and me.status = 'ACTIVE') as already_member,
                    exists (select 1 from approval_request ar where ar.type = 'GROUP_JOIN' and ar.subject_id = g.id and ar.status = 'PENDING'
                        and ar.payload ->> 'personId' = cast(:p as text)) as already_requested
                from small_group g join branch b on b.id = g.branch_id
                where g.organization_id = :o and g.status = 'ACTIVE' and g.open_to_join = true order by g.name
                """, ps, (rs, i) -> {
            int day = rs.getInt("meeting_day");
            Integer meetingDay = rs.wasNull() ? null : day;
            java.sql.Time mt = rs.getTime("meeting_time");
            int cap = rs.getInt("capacity");
            Integer capacity = rs.wasNull() ? null : cap;
            return new OpenGroupRow((UUID) rs.getObject("id"), rs.getString("name"), rs.getString("category"), rs.getString("audience"),
                    rs.getString("branch_name"), meetingDay, mt == null ? null : mt.toLocalTime(), rs.getString("location"), capacity,
                    rs.getInt("active_count"), rs.getBoolean("already_member"), rs.getBoolean("already_requested"));
        });
    }

    /**
     * Solicitar ingreso a un grupo abierto: crea la misma solicitud de aprobación (M21, tipo {@code GROUP_JOIN}) que
     * ya usa {@code GroupJoinService} en el panel admin (misma forma de payload) — así la ficha del grupo la ve y la
     * decide exactamente igual, líder o administración. Las reglas de validación (grupo abierto, cupo, ya es
     * integrante, solicitud duplicada, menor/audiencia, "varios grupos por persona") se repiten aquí en vez de
     * reutilizar {@code GroupJoinService.request}: ese método pide un {@code AccessScope} de alcance de sede, que un
     * MEMBER del portal no tiene ni debería necesitar para pedir su propio ingreso (mismo criterio de la clase).
     */
    @Transactional
    public void requestJoinGroup(AuthenticatedActor actor, UUID groupId, String message) {
        List<Map<String, Object>> gs = jdbc.queryForList(
                "select branch_id, name, status, open_to_join, audience, capacity from small_group where id = :id and organization_id = :o",
                new MapSqlParameterSource("id", groupId).addValue("o", actor.organizationId()));
        if (gs.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        Map<String, Object> g = gs.get(0);
        if (!"ACTIVE".equals(g.get("status")) || !Boolean.TRUE.equals(g.get("open_to_join"))) {
            throw new Exceptions("error.group.notOpen", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        UUID branchId = (UUID) g.get("branch_id");
        String groupName = (String) g.get("name");
        Integer capacity = g.get("capacity") == null ? null : ((Number) g.get("capacity")).intValue();
        MapSqlParameterSource mp = new MapSqlParameterSource("g", groupId).addValue("p", actor.ownerId());
        if (capacity != null) {
            Integer active = jdbc.queryForObject("select count(*) from group_member where group_id = :g and status = 'ACTIVE'", mp, Integer.class);
            if (active != null && active >= capacity) {
                throw new Exceptions("error.group.full", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
        Integer already = jdbc.queryForObject("select count(*) from group_member where group_id = :g and person_id = :p and status = 'ACTIVE'", mp, Integer.class);
        if (already != null && already > 0) {
            throw new Exceptions("error.group.alreadyMember", HttpStatus.CONFLICT);
        }
        Integer pending = jdbc.queryForObject(
                "select count(*) from approval_request where type = 'GROUP_JOIN' and subject_id = :g and status = 'PENDING' and payload ->> 'personId' = cast(:p as text)",
                mp, Integer.class);
        if (pending != null && pending > 0) {
            throw new Exceptions("error.group.requestExists", HttpStatus.CONFLICT);
        }
        MapSqlParameterSource pp = new MapSqlParameterSource("p", actor.ownerId()).addValue("o", actor.organizationId());
        List<Map<String, Object>> ps = jdbc.queryForList("select trim(first_name || ' ' || last_name) as name, birth_date from person where id = :p and organization_id = :o", pp);
        if (ps.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        String personName = (String) ps.get(0).get("name");
        java.sql.Date bd = (java.sql.Date) ps.get(0).get("birth_date");
        LocalDate birthDate = bd == null ? null : bd.toLocalDate();
        if (isMinor(birthDate) && "ADULT".equals(g.get("audience"))) {
            throw new Exceptions("error.group.minorNotAllowed", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        boolean allowMultiple = jdbc.query("select allow_multiple_groups from group_rules where organization_id = :o",
                new MapSqlParameterSource("o", actor.organizationId()), (rs, i) -> rs.getBoolean(1)).stream().findFirst().orElse(true);
        if (!allowMultiple) {
            Integer other = jdbc.queryForObject("""
                    select count(*) from group_member m join small_group x on x.id = m.group_id
                    where m.person_id = :p and m.status = 'ACTIVE' and x.status <> 'CLOSED'
                    """, mp, Integer.class);
            if (other != null && other > 0) {
                throw new Exceptions("error.group.multipleNotAllowed", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
        String reason = trim(message, 300, "mensaje");
        String branchName = jdbc.query("select name from branch where id = :b", new MapSqlParameterSource("b", branchId), (rs, i) -> rs.getString(1))
                .stream().findFirst().orElse("");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("personId", actor.ownerId().toString());
        payload.put("personName", personName);
        payload.put("groupName", groupName);
        payload.put("branchName", branchName);
        approvals.open(new ApprovalEngine.NewRequest(actor.organizationId(), branchId, null, "GROUP_JOIN", "GROUP", groupId, actor.ownerId(), reason, payload));
        List<UUID> leaders = jdbc.queryForList("select person_id from group_member where group_id = :g and status = 'ACTIVE' and role in ('LEADER','COLEADER')",
                new MapSqlParameterSource("g", groupId), UUID.class);
        leaders.remove(actor.ownerId());
        if (!leaders.isEmpty()) {
            notifications.toPersons(NotificationType.GROUP_JOIN_REQUESTED, actor.organizationId(), leaders,
                    Map.of("person", personName, "group", groupName), "/app/groups/" + groupId, null);
        }
    }

    private static final String MY_SHIFT_JOIN = """
            from shift_assignment a
            join shift_slot sl on sl.id = a.slot_id
            join service_plan p on p.id = sl.plan_id
            join branch_ministry bm on bm.id = sl.branch_ministry_id
            join ministry m on m.id = bm.ministry_id
            left join ministry_position mp on mp.id = sl.position_id
            """;

    /** Bloque SERVICE (seguimiento 2026-09-26): turnos propios de M11b. Solo planes {@code PUBLISHED}/{@code CLOSED}:
     * en {@code DRAFT} el coordinador puede proponer turnos sin haber avisado todavía (ver {@code ShiftAssignmentService.assign},
     * que solo notifica cuando el plan está publicado) — mostrarlos antes filtraría el trabajo en curso del coordinador. */
    @Transactional(readOnly = true)
    public List<MyShiftRow> myShifts(AuthenticatedActor actor) {
        MapSqlParameterSource ps = new MapSqlParameterSource("o", actor.organizationId()).addValue("p", actor.ownerId());
        return jdbc.query("select a.id, p.id as plan_id, sl.id as slot_id, m.name as ministry_name, mp.name as position_name,"
                + " p.plan_date, sl.start_time, sl.end_time, a.status, sl.notes " + MY_SHIFT_JOIN
                + " where p.organization_id = :o and a.person_id = :p and p.status in ('PUBLISHED','CLOSED') order by p.plan_date desc, sl.start_time",
                ps, (rs, i) -> new MyShiftRow((UUID) rs.getObject("id"), (UUID) rs.getObject("plan_id"), (UUID) rs.getObject("slot_id"),
                        rs.getString("ministry_name"), rs.getString("position_name"), rs.getDate("plan_date").toLocalDate(),
                        rs.getTime("start_time").toLocalTime(), rs.getTime("end_time").toLocalTime(), rs.getString("status"), rs.getString("notes")));
    }

    /** Confirmar mi propio turno propuesto. Mismo candado que el resto del portal: la fila se busca por {@code person_id = actor.ownerId()}
     * directamente (nunca por el id de la asignación sola), así que un turno ajeno da 404, no 403. */
    @Transactional
    public void confirmMyShift(AuthenticatedActor actor, UUID assignmentId) {
        Map<String, Object> a = lockOwnShift(actor, assignmentId);
        if (!"PROPOSED".equals(a.get("status"))) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, a.get("status"));
        }
        jdbc.update("update shift_assignment set status = 'CONFIRMED', responded_at = :at, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(Instant.now())).addValue("id", assignmentId));
    }

    /** [V12 de M11b, repetido aquí] Dentro de la ventana de bloqueo exige motivo; siempre avisa a quien lidera el ministerio. */
    @Transactional
    public void declineMyShift(AuthenticatedActor actor, UUID assignmentId, String reason) {
        Map<String, Object> a = lockOwnShift(actor, assignmentId);
        String status = (String) a.get("status");
        if (!Set.of("PROPOSED", "CONFIRMED").contains(status)) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, status);
        }
        LocalDate planDate = ((java.sql.Date) a.get("plan_date")).toLocalDate();
        java.time.LocalTime start = ((java.sql.Time) a.get("start_time")).toLocalTime();
        ZoneId zone = zoneOf((UUID) a.get("branch_id"));
        long hoursLeft = Duration.between(Instant.now(), planDate.atTime(start).atZone(zone).toInstant()).toHours();
        int lockHours = jdbc.query("select decline_lock_hours from volunteer_rules where organization_id = :o",
                new MapSqlParameterSource("o", actor.organizationId()), (rs, i) -> rs.getInt(1)).stream().findFirst().orElse(24);
        String trimmed = trim(reason, 300, "motivo");
        if (hoursLeft < lockHours && trimmed == null) {
            throw new Exceptions("error.shift.declineReason", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        jdbc.update("update shift_assignment set status = 'DECLINED', decline_reason = :r, responded_at = :at, version = version + 1 where id = :id",
                new MapSqlParameterSource("r", trimmed).addValue("at", Timestamp.from(Instant.now())).addValue("id", assignmentId));
        UUID leaderId = (UUID) a.get("leader_person_id");
        if (leaderId != null) {
            notifications.toPersons(NotificationType.SHIFT_DECLINED, actor.organizationId(), List.of(leaderId),
                    Map.of("ministry", (String) a.get("ministry_name"), "date", planDate.toString(), "time", start.toString()), "/app/volunteer-scheduling", null);
        }
    }

    /** M24 N4 · bloque PASTORAL_CARE: casos propios, nunca el contenido de {@code case_note}. */
    @Transactional(readOnly = true)
    public List<MyPastoralCaseRow> myPastoralCases(AuthenticatedActor actor) {
        return jdbc.query("""
                select c.id, c.type, c.priority, c.status, c.created_at, c.resolved_at, c.closed_at,
                       ap.first_name as a_fn, ap.last_name as a_ln
                from pastoral_case c left join person ap on ap.id = c.assigned_to
                where c.person_id = :p and c.organization_id = :o order by c.created_at desc limit 100
                """, new MapSqlParameterSource("o", actor.organizationId()).addValue("p", actor.ownerId()), (rs, i) -> new MyPastoralCaseRow(
                (UUID) rs.getObject("id"), rs.getString("type"), rs.getString("priority"), rs.getString("status"),
                rs.getString("a_fn") == null ? null : (rs.getString("a_fn") + " " + rs.getString("a_ln")).trim(),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("resolved_at") == null ? null : rs.getTimestamp("resolved_at").toInstant(),
                rs.getTimestamp("closed_at") == null ? null : rs.getTimestamp("closed_at").toInstant()));
    }

    /** Alta propia de un caso pastoral con {@code source = 'PORTAL'} (valor ya previsto en {@code ck_pc_source} desde M12);
     * sin asignación automática (el staff triagea desde el panel admin) y sin due date (eso es criterio de staff). La
     * nota inicial, si se envía, se guarda como {@code case_note} normal — el miembro es su propio autor. */
    @Transactional
    public void requestPastoralCare(AuthenticatedActor actor, String type, String note) {
        String t = type != null && !type.isBlank() ? type.trim().toUpperCase() : "OTHER";
        if (!PASTORAL_TYPES.contains(t)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo");
        }
        UUID branchId = actor.activeBranchId();
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("insert into pastoral_case (id, organization_id, branch_id, person_id, type, priority, status, source, confidentiality,"
                        + " created_at, created_by, updated_at, updated_by) values (:id, :o, :b, :p, :t, 'NORMAL', 'OPEN', 'PORTAL', 'STANDARD',"
                        + " :at, :p, :at, :p)",
                new MapSqlParameterSource("id", id).addValue("o", actor.organizationId()).addValue("b", branchId).addValue("p", actor.ownerId())
                        .addValue("t", t).addValue("at", now));
        String trimmed = trim(note, 2000, "nota");
        if (trimmed != null) {
            jdbc.update("insert into case_note (id, case_id, author_id, text, restricted, created_at) values (:id, :c, :a, :t, false, :at)",
                    new MapSqlParameterSource("id", UUID.randomUUID()).addValue("c", id).addValue("a", actor.ownerId())
                            .addValue("t", cipher.encrypt(trimmed)).addValue("at", now));
        }
    }

    /** M24 N4 · bloque PASTORAL_CARE: peticiones de oración propias (M12 {@code prayer_request}). */
    @Transactional(readOnly = true)
    public List<MyPrayerRequestRow> myPrayerRequests(AuthenticatedActor actor) {
        return jdbc.query("""
                select id, text, category, visibility, anonymous, status, moderation, answered_at, testimony, created_at
                from prayer_request where requested_by = :p and organization_id = :o order by created_at desc limit 100
                """, new MapSqlParameterSource("o", actor.organizationId()).addValue("p", actor.ownerId()), (rs, i) -> new MyPrayerRequestRow(
                (UUID) rs.getObject("id"), rs.getString("text"), rs.getString("category"), rs.getString("visibility"), rs.getBoolean("anonymous"),
                rs.getString("status"), rs.getString("moderation"), rs.getTimestamp("answered_at") == null ? null : rs.getTimestamp("answered_at").toInstant(),
                rs.getString("testimony"), rs.getTimestamp("created_at").toInstant()));
    }

    /** Replica {@code PrayerService.create()} sin {@code AccessScope}: {@code requested_by} y {@code branch_id} se
     * resuelven directamente del actor del portal, igual criterio [V10]/[V11] (largo de texto, moderación pendiente
     * solo para visibilidad CONGREGATION). */
    @Transactional
    public void submitPrayerRequest(AuthenticatedActor actor, String text, String category, String visibility, Boolean anonymous) {
        if (text == null || text.isBlank()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "petición");
        }
        String t = text.trim();
        if (t.length() < 5 || t.length() > 1000) {
            throw new Exceptions("error.prayer.textLength", HttpStatus.BAD_REQUEST, 5, 1000);
        }
        String cat = null;
        if (category != null && !category.isBlank()) {
            cat = category.trim().toUpperCase();
            Integer n = jdbc.queryForObject("select count(*) from catalog_item where type = 'PRAYER_CATEGORY' and code = :c and active"
                            + " and (organization_id is null or organization_id = :o)",
                    new MapSqlParameterSource("c", cat).addValue("o", actor.organizationId()), Integer.class);
            if (n == null || n == 0) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "categoría");
            }
        }
        String vis = visibility != null && !visibility.isBlank() ? visibility.trim().toUpperCase() : "PRIVATE";
        if (!PRAYER_VISIBILITY.contains(vis)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "visibilidad");
        }
        boolean anon = Boolean.TRUE.equals(anonymous);
        String moderation = "CONGREGATION".equals(vis) ? "PENDING" : "NA";
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("insert into prayer_request (id, organization_id, branch_id, requested_by, text, category, visibility, anonymous, moderation,"
                        + " status, created_at, updated_at) values (:id, :o, :b, :p, :t, :c, :v, :a, :m, 'OPEN', :at, :at)",
                new MapSqlParameterSource("id", id).addValue("o", actor.organizationId()).addValue("b", actor.activeBranchId())
                        .addValue("p", actor.ownerId()).addValue("t", t).addValue("c", cat).addValue("v", vis).addValue("a", anon)
                        .addValue("m", moderation).addValue("at", now));
    }

    /** M24 N4 · bloque TRAINING: matrículas propias de M13 ({@code enrollment}). */
    @Transactional(readOnly = true)
    public List<MyEnrollmentRow> myEnrollments(AuthenticatedActor actor) {
        return jdbc.query("""
                select e.id, k.name as course_name, k.hours, cc.start_date, cc.end_date, cc.day_of_week, cc.start_time, cc.end_time, cc.location,
                       t.first_name as t_fn, t.last_name as t_ln, e.status, e.final_grade, e.enrolled_at
                from enrollment e join course_class cc on cc.id = e.class_id join course k on k.id = cc.course_id
                left join person t on t.id = cc.teacher_person_id
                where e.person_id = :p and e.organization_id = :o order by e.enrolled_at desc limit 100
                """, new MapSqlParameterSource("o", actor.organizationId()).addValue("p", actor.ownerId()), (rs, i) -> new MyEnrollmentRow(
                (UUID) rs.getObject("id"), rs.getString("course_name"), rs.getInt("hours"),
                rs.getDate("start_date").toLocalDate(), rs.getDate("end_date").toLocalDate(),
                (Integer) rs.getObject("day_of_week"), rs.getTime("start_time") == null ? null : rs.getTime("start_time").toLocalTime(),
                rs.getTime("end_time") == null ? null : rs.getTime("end_time").toLocalTime(), rs.getString("location"),
                rs.getString("t_fn") == null ? null : (rs.getString("t_fn") + " " + rs.getString("t_ln")).trim(), rs.getString("status"),
                rs.getBigDecimal("final_grade"), rs.getTimestamp("enrolled_at").toInstant()));
    }

    /** Dictados abiertos para inscribirse (M13 {@code course_class} en PLANNED/IN_PROGRESS), a nivel de toda la
     * organización (igual criterio que {@code openGroups}: un miembro puede tomar un curso en otra sede). */
    @Transactional(readOnly = true)
    public List<OpenClassRow> openClasses(AuthenticatedActor actor) {
        return jdbc.query("""
                select cc.id, k.name as course_name, k.hours, b.name as branch_name, cc.start_date, cc.end_date, cc.day_of_week, cc.start_time,
                       cc.end_time, cc.location, t.first_name as t_fn, t.last_name as t_ln, cc.capacity,
                       (select count(*) from enrollment e2 where e2.class_id = cc.id and e2.status <> 'WITHDRAWN') as active_count,
                       exists(select 1 from enrollment e3 where e3.class_id = cc.id and e3.person_id = :p and e3.status <> 'WITHDRAWN') as already
                from course_class cc join course k on k.id = cc.course_id join branch b on b.id = cc.branch_id
                left join person t on t.id = cc.teacher_person_id
                where cc.organization_id = :o and cc.status in ('PLANNED', 'IN_PROGRESS') order by cc.start_date asc limit 200
                """, new MapSqlParameterSource("o", actor.organizationId()).addValue("p", actor.ownerId()), (rs, i) -> new OpenClassRow(
                (UUID) rs.getObject("id"), rs.getString("course_name"), rs.getInt("hours"), rs.getString("branch_name"),
                rs.getDate("start_date").toLocalDate(), rs.getDate("end_date").toLocalDate(), (Integer) rs.getObject("day_of_week"),
                rs.getTime("start_time") == null ? null : rs.getTime("start_time").toLocalTime(),
                rs.getTime("end_time") == null ? null : rs.getTime("end_time").toLocalTime(), rs.getString("location"),
                rs.getString("t_fn") == null ? null : (rs.getString("t_fn") + " " + rs.getString("t_ln")).trim(), rs.getInt("capacity"),
                rs.getInt("active_count"), rs.getBoolean("already")));
    }

    /** Replica {@code EnrollmentService.enroll()} sin la ruta de excepción de prerrequisito (esa exige acción
     * {@code O} de staff): persona activa, dictado abierto, sin aprobación previa del mismo curso, prerrequisito
     * aprobado y cupo disponible. */
    @Transactional
    public void requestEnroll(AuthenticatedActor actor, UUID classId) {
        List<String> pst = jdbc.queryForList("select status from person where id = :p and organization_id = :o",
                new MapSqlParameterSource("p", actor.ownerId()).addValue("o", actor.organizationId()), String.class);
        if (pst.isEmpty() || !"ACTIVE".equals(pst.get(0))) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, pst.isEmpty() ? "?" : pst.get(0));
        }
        List<Map<String, Object>> cls = jdbc.queryForList(
                "select cc.id, cc.status, cc.capacity, cc.branch_id, k.id as course_id, k.name as course_name, k.curriculum_id, k.order_num"
                        + " from course_class cc join course k on k.id = cc.course_id where cc.id = :id and cc.organization_id = :o",
                new MapSqlParameterSource("id", classId).addValue("o", actor.organizationId()));
        if (cls.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        Map<String, Object> c = cls.get(0);
        String status = (String) c.get("status");
        if (!OPEN_CLASS_STATUS.contains(status)) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, status);
        }
        UUID courseId = (UUID) c.get("course_id");
        UUID curriculumId = (UUID) c.get("curriculum_id");
        Integer orderNum = (Integer) c.get("order_num");
        Integer approvedAlready = jdbc.queryForObject("select count(*) from enrollment e join course_class t on t.id = e.class_id"
                        + " where e.person_id = :p and t.course_id = :co and e.status = 'APPROVED'",
                new MapSqlParameterSource("p", actor.ownerId()).addValue("co", courseId), Integer.class);
        if (approvedAlready != null && approvedAlready > 0) {
            throw new Exceptions("error.training.alreadyApproved", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (curriculumId != null && orderNum != null && orderNum > 1) {
            boolean has = jdbc.queryForObject("select count(*) from enrollment e join course_class t on t.id = e.class_id join course k on k.id = t.course_id"
                            + " where e.person_id = :p and k.curriculum_id = :cur and k.order_num = :ord and e.status = 'APPROVED'",
                    new MapSqlParameterSource("p", actor.ownerId()).addValue("cur", curriculumId).addValue("ord", orderNum - 1), Integer.class) > 0;
            if (!has) {
                String required = jdbc.queryForObject("select name from course where curriculum_id = :cur and order_num = :ord",
                        new MapSqlParameterSource("cur", curriculumId).addValue("ord", orderNum - 1), String.class);
                throw new Exceptions("error.training.prerequisite", HttpStatus.UNPROCESSABLE_ENTITY, required);
            }
        }
        Integer enrolled = jdbc.queryForObject("select count(*) from enrollment where class_id = :id and status <> 'WITHDRAWN'",
                new MapSqlParameterSource("id", classId), Integer.class);
        int capacity = (Integer) c.get("capacity");
        if (enrolled != null && enrolled >= capacity) {
            throw new Exceptions("error.training.classFull", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        try {
            jdbc.update("insert into enrollment (id, organization_id, branch_id, person_id, class_id, status, enrolled_at, created_by)"
                            + " values (:id, :o, :b, :p, :c, 'ENROLLED', :at, :by)",
                    new MapSqlParameterSource("id", id).addValue("o", actor.organizationId()).addValue("b", c.get("branch_id"))
                            .addValue("p", actor.ownerId()).addValue("c", classId).addValue("at", now).addValue("by", actor.ownerId()));
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.training.alreadyEnrolled", HttpStatus.CONFLICT);
        }
    }

    /** Retiro propio de una matrícula ENROLLED (anti-IDOR: bloquea filtrando por {@code person_id} en el WHERE); el
     * motivo es obligatorio, igual criterio que {@code EnrollmentService.status()} para WITHDRAWN. */
    @Transactional
    public void withdrawEnrollment(AuthenticatedActor actor, UUID enrollmentId, String reason) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, status from enrollment where id = :id and person_id = :p and organization_id = :o for update",
                new MapSqlParameterSource("id", enrollmentId).addValue("p", actor.ownerId()).addValue("o", actor.organizationId()));
        if (rows.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        String status = (String) rows.get(0).get("status");
        if (!"ENROLLED".equals(status)) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, status);
        }
        String trimmed = trim(reason, 300, "motivo");
        if (trimmed == null) {
            throw new Exceptions("error.training.statusReasonRequired", HttpStatus.BAD_REQUEST);
        }
        jdbc.update("update enrollment set status = 'WITHDRAWN', status_reason = :r, resolved_at = :at, version = version + 1 where id = :id",
                new MapSqlParameterSource("r", trimmed).addValue("at", Timestamp.from(Instant.now())).addValue("id", enrollmentId));
    }

    /** M24 N4 · bloque EVENTS: eventos publicados de M14 (`org_event`), a nivel de toda la organización (igual
     * criterio que {@code openGroups}/{@code openClasses}). Por evento se agregan sus preguntas ({@code event_question})
     * en una consulta aparte — pocos eventos y pocas preguntas por evento, sin necesidad de un join complejo. */
    @Transactional(readOnly = true)
    public List<OpenEventRow> openEvents(AuthenticatedActor actor) {
        List<OpenEventRow> base = jdbc.query("""
                select e.id, e.name, e.type_code, b.name as branch_name, e.start_at, e.end_at, e.location, e.online_url, e.capacity,
                       e.waitlist_enabled, e.min_age, e.guests_max,
                       (select count(*) from event_registration r where r.event_id = e.id and r.status = 'REGISTERED') as reg_count,
                       (select t.amount from event_price_tier t where t.event_id = e.id and t.category = 'MEMBER') as member_amount,
                       (select t.currency from event_price_tier t where t.event_id = e.id and t.category = 'MEMBER') as currency,
                       exists(select 1 from event_registration r2 where r2.event_id = e.id and r2.person_id = :p and r2.status <> 'CANCELLED') as already
                from org_event e left join branch b on b.id = e.branch_id
                where e.organization_id = :o and e.status = 'PUBLISHED' order by e.start_at asc limit 200
                """, new MapSqlParameterSource("o", actor.organizationId()).addValue("p", actor.ownerId()), (rs, i) -> new OpenEventRow(
                (UUID) rs.getObject("id"), rs.getString("name"), rs.getString("type_code"), rs.getString("branch_name"),
                rs.getTimestamp("start_at").toInstant(), rs.getTimestamp("end_at").toInstant(), rs.getString("location"), rs.getString("online_url"),
                (Integer) rs.getObject("capacity"), rs.getInt("reg_count"), rs.getBoolean("waitlist_enabled"), (Integer) rs.getObject("min_age"),
                rs.getInt("guests_max"), rs.getBigDecimal("member_amount"), rs.getString("currency"), rs.getBoolean("already"), List.of()));
        return base.stream().map(e -> new OpenEventRow(e.id(), e.name(), e.typeCode(), e.branchName(), e.startAt(), e.endAt(), e.location(),
                e.onlineUrl(), e.capacity(), e.registeredCount(), e.waitlistEnabled(), e.minAge(), e.guestsMax(), e.memberAmount(), e.currency(),
                e.alreadyRegistered(), eventQuestions(e.id()))).toList();
    }

    private List<EventQuestionRow> eventQuestions(UUID eventId) {
        return jdbc.query("select id, label, type, options, required from event_question where event_id = :id order by sort_order",
                new MapSqlParameterSource("id", eventId), (rs, i) -> new EventQuestionRow((UUID) rs.getObject("id"), rs.getString("label"),
                        rs.getString("type"), rs.getString("options"), rs.getBoolean("required")));
    }

    /** M24 N4 · bloque EVENTS: inscripciones propias. */
    @Transactional(readOnly = true)
    public List<MyRegistrationRow> myRegistrations(AuthenticatedActor actor) {
        return jdbc.query("""
                select r.id, r.event_id, e.name as event_name, e.start_at, e.end_at, e.location, r.status, r.payment_status, r.amount, r.currency,
                       r.guests, r.ticket_code, r.created_at
                from event_registration r join org_event e on e.id = r.event_id
                where r.person_id = :p and e.organization_id = :o order by e.start_at desc limit 100
                """, new MapSqlParameterSource("o", actor.organizationId()).addValue("p", actor.ownerId()), (rs, i) -> new MyRegistrationRow(
                (UUID) rs.getObject("id"), (UUID) rs.getObject("event_id"), rs.getString("event_name"), rs.getTimestamp("start_at").toInstant(),
                rs.getTimestamp("end_at").toInstant(), rs.getString("location"), rs.getString("status"), rs.getString("payment_status"),
                rs.getBigDecimal("amount"), rs.getString("currency"), rs.getInt("guests"), rs.getString("ticket_code"), rs.getTimestamp("created_at").toInstant()));
    }

    /** Replica {@code EventRegistrationService.registerCore()} con {@code category = 'MEMBER'} fijo, sin tutor (es
     * autoservicio sobre uno mismo) y sin la ventana de excepción (esa exige acción {@code O} de staff):
     * persona activa, evento publicado y dentro de la ventana de inscripción, edad mínima, cupo de acompañantes y
     * cupo del evento (con lista de espera si corresponde). {@code source = 'PUBLIC'} — ver Javadoc de {@link OpenEventRow}. */
    @Transactional
    public void registerForEvent(AuthenticatedActor actor, UUID eventId, Integer guests, Map<String, String> answers) {
        List<String> pst = jdbc.queryForList("select status from person where id = :p and organization_id = :o",
                new MapSqlParameterSource("p", actor.ownerId()).addValue("o", actor.organizationId()), String.class);
        if (pst.isEmpty() || !"ACTIVE".equals(pst.get(0))) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, pst.isEmpty() ? "?" : pst.get(0));
        }
        List<Map<String, Object>> evs = jdbc.queryForList(
                "select id, status, capacity, waitlist_enabled, reg_opens_at, reg_closes_at, min_age, guests_max from org_event"
                        + " where id = :id and organization_id = :o",
                new MapSqlParameterSource("id", eventId).addValue("o", actor.organizationId()));
        if (evs.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        Map<String, Object> e = evs.get(0);
        if (!"PUBLISHED".equals(e.get("status"))) {
            throw new Exceptions("error.event.registrationClosed", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Instant now = Instant.now();
        Timestamp regOpens = (Timestamp) e.get("reg_opens_at");
        Timestamp regCloses = (Timestamp) e.get("reg_closes_at");
        boolean windowOpen = (regOpens == null || !now.isBefore(regOpens.toInstant())) && (regCloses == null || !now.isAfter(regCloses.toInstant()));
        if (!windowOpen) {
            throw new Exceptions("error.event.registrationClosed", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Integer minAge = (Integer) e.get("min_age");
        if (minAge != null && minAge > 0) {
            List<java.sql.Date> birth = jdbc.query("select birth_date from person where id = :id",
                    new MapSqlParameterSource("id", actor.ownerId()), (rs, i) -> rs.getDate(1));
            LocalDate b = birth.isEmpty() || birth.get(0) == null ? null : birth.get(0).toLocalDate();
            int age = b == null ? Integer.MAX_VALUE : Period.between(b, LocalDate.now()).getYears();
            if (age < minAge) {
                throw new Exceptions("error.event.minAge", HttpStatus.UNPROCESSABLE_ENTITY, minAge);
            }
        }
        int guestsCount = guests == null ? 0 : Math.max(0, guests);
        int guestsMax = (Integer) e.get("guests_max");
        if (guestsCount > guestsMax) {
            throw new Exceptions("error.event.guestsExceeded", HttpStatus.UNPROCESSABLE_ENTITY, guestsMax);
        }
        List<BigDecimal> amountRows = jdbc.query("select amount from event_price_tier where event_id = :e and category = 'MEMBER'",
                new MapSqlParameterSource("e", eventId), (rs, i) -> rs.getBigDecimal(1));
        BigDecimal amount = amountRows.isEmpty() ? BigDecimal.ZERO : amountRows.get(0);
        List<String> currencyRows = jdbc.query("select currency from event_price_tier where event_id = :e and category = 'MEMBER'",
                new MapSqlParameterSource("e", eventId), (rs, i) -> rs.getString(1));
        String currency = currencyRows.isEmpty() ? "PEN" : currencyRows.get(0);
        Integer capacity = (Integer) e.get("capacity");
        boolean waitlistEnabled = (Boolean) e.get("waitlist_enabled");
        Integer registered = jdbc.queryForObject("select count(*) from event_registration where event_id = :id and status = 'REGISTERED'",
                new MapSqlParameterSource("id", eventId), Integer.class);
        String status = "REGISTERED";
        if (registered != null && capacity != null && registered >= capacity) {
            if (!waitlistEnabled) {
                throw new Exceptions("error.event.full", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            status = "WAITLISTED";
        }
        UUID id = UUID.randomUUID();
        Timestamp nowTs = Timestamp.from(now);
        try {
            jdbc.update("insert into event_registration (id, event_id, person_id, category, status, payment_status, amount, currency, guests,"
                            + " answers, source, created_at, created_by, updated_at, updated_by)"
                            + " values (:id, :e, :p, 'MEMBER', :s, :ps, :a, :cu, :gu, cast(:ans as jsonb), 'PUBLIC', :at, :by, :at, :by)",
                    new MapSqlParameterSource("id", id).addValue("e", eventId).addValue("p", actor.ownerId()).addValue("s", status)
                            .addValue("ps", amount.signum() == 0 ? "WAIVED" : "PENDING").addValue("a", amount).addValue("cu", currency)
                            .addValue("gu", guestsCount).addValue("ans", writeAnswers(answers)).addValue("at", nowTs).addValue("by", actor.ownerId()));
        } catch (DataIntegrityViolationException ex) {
            throw new Exceptions("error.event.alreadyRegistered", HttpStatus.CONFLICT);
        }
        if ("REGISTERED".equals(status)) {
            issueTicket(id);
        }
    }

    private String writeAnswers(Map<String, String> answers) {
        try {
            return mapper.writeValueAsString(answers == null ? Map.of() : answers);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void issueTicket(UUID registrationId) {
        String code;
        int attempts = 0;
        do {
            code = EventSupport.ticketCode();
            attempts++;
        } while (attempts < 5 && jdbc.queryForObject("select count(*) from event_registration where ticket_code = :c",
                new MapSqlParameterSource("c", code), Integer.class) > 0);
        jdbc.update("update event_registration set ticket_code = :c where id = :id",
                new MapSqlParameterSource("c", code).addValue("id", registrationId));
    }

    /** Retiro propio de una inscripción (anti-IDOR: bloquea filtrando por {@code person_id} en el WHERE). Solo cubre
     * el caso "seguro": dentro del plazo de cancelación y sin pago ya confirmado — lo demás exige staff (ver Javadoc
     * de {@link pe.dcs.app.features.portal.dto.PortalDtos.CancelRegistrationRequest}). Promueve a la siguiente
     * persona en lista de espera si el cupo liberado era un REGISTERED activo, igual criterio que
     * {@code EventRegistrationService.promoteOne()}. */
    @Transactional
    public void cancelRegistration(AuthenticatedActor actor, UUID registrationId, String reason) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select r.id, r.status, r.payment_status, r.event_id from event_registration r join org_event e on e.id = r.event_id"
                        + " where r.id = :id and r.person_id = :p and e.organization_id = :o for update of r",
                new MapSqlParameterSource("id", registrationId).addValue("p", actor.ownerId()).addValue("o", actor.organizationId()));
        if (rows.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        Map<String, Object> r = rows.get(0);
        String status = (String) r.get("status");
        if ("CANCELLED".equals(status)) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, status);
        }
        UUID eventId = (UUID) r.get("event_id");
        List<Timestamp> deadlineRows = jdbc.query("select cancel_deadline from org_event where id = :id",
                new MapSqlParameterSource("id", eventId), (rs, i) -> rs.getTimestamp(1));
        Timestamp deadline = deadlineRows.isEmpty() ? null : deadlineRows.get(0);
        boolean withinDeadline = deadline == null || !Instant.now().isAfter(deadline.toInstant());
        if (!withinDeadline) {
            throw new Exceptions("error.event.cancelDeadline", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if ("PAID".equals(r.get("payment_status"))) {
            throw new Exceptions("error.event.refundRequired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String trimmed = trim(reason, 500, "motivo");
        boolean wasRegistered = "REGISTERED".equals(status);
        jdbc.update("update event_registration set status = 'CANCELLED', cancel_reason = :r, updated_at = :at, updated_by = :by, version = version + 1"
                        + " where id = :id",
                new MapSqlParameterSource("r", trimmed).addValue("at", Timestamp.from(Instant.now())).addValue("by", actor.ownerId())
                        .addValue("id", registrationId));
        if (wasRegistered) {
            promoteOneWaitlisted(actor.organizationId(), eventId);
        }
    }

    /** M24 N4 · bloque GIVING (seguimiento 2026-09-26): historial propio, solo lectura — ver Javadoc de
     * {@link MyGivingRow} para el porqué de cada exclusión y del criterio de la "constancia anual". Sin fila propia
     * en {@code fin_donor} (nunca donó) devuelve lista vacía, nunca un error: es el mismo caso que un miembro sin
     * matrículas o sin turnos, no una situación excepcional. */
    @Transactional(readOnly = true)
    public List<MyGivingRow> myGiving(AuthenticatedActor actor) {
        return jdbc.query("select m.id, m.movement_date, m.category, fu.name as fund_name, m.amount, m.currency, m.method, m.receipt_no, m.status,"
                        + " m.description from fin_movement m join fin_fund fu on fu.id = m.fund_id join fin_donor d on d.id = m.donor_id"
                        + " where d.person_id = :p and m.organization_id = :o and m.type = 'INCOME' order by m.movement_date desc, m.created_at desc",
                new MapSqlParameterSource("p", actor.ownerId()).addValue("o", actor.organizationId()),
                (rs, i) -> new MyGivingRow((UUID) rs.getObject("id"), rs.getDate("movement_date").toLocalDate(), rs.getString("category"),
                        rs.getString("fund_name"), rs.getBigDecimal("amount"), rs.getString("currency"), rs.getString("method"),
                        rs.getString("receipt_no"), rs.getString("status"), rs.getString("description")));
    }

    /** M24 N4 · bloque SPACES (seguimiento 2026-09-26): ver Javadoc de {@link OpenSpacesResponse} para el porqué de
     * {@code canRequest} — primer y único punto del código que consulta {@code space_rules.reservation_requesters}. */
    @Transactional(readOnly = true)
    public OpenSpacesResponse openSpaces(AuthenticatedActor actor) {
        boolean canRequest = canRequestReservation(actor);
        List<OpenSpaceRow> spaces = jdbc.query(
                "select s.id, s.name, s.type_code, b.name as branch_name, s.capacity, s.equipment, s.requires_approval, s.open_from, s.open_to"
                        + " from space s join branch b on b.id = s.branch_id where s.organization_id = :o and s.status = 'ACTIVE' order by b.name, s.name",
                new MapSqlParameterSource("o", actor.organizationId()), (rs, i) -> {
                    java.sql.Time of = rs.getTime("open_from");
                    java.sql.Time ot = rs.getTime("open_to");
                    return new OpenSpaceRow((UUID) rs.getObject("id"), rs.getString("name"), rs.getString("type_code"), rs.getString("branch_name"),
                            (Integer) rs.getObject("capacity"), equipmentList(rs.getString("equipment")), rs.getBoolean("requires_approval"),
                            of == null ? null : of.toLocalTime(), ot == null ? null : ot.toLocalTime());
                });
        return new OpenSpacesResponse(canRequest, spaces);
    }

    private List<String> equipmentList(String json) {
        try {
            return json == null ? List.of() : mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    /** {@code STAFF} (por defecto si la organización nunca configuró {@code space_rules}) → false: el portal no
     * ofrece autoservicio. {@code ANY_MEMBER} → true para cualquier miembro. {@code LEADERS} → true solo si el
     * actor lidera/colidera un grupo activo o es líder de un ministerio de sede — no hay un catálogo único de
     * "líder" en el modelo, así que se combinan los dos lugares donde ese rol ya existe. */
    private boolean canRequestReservation(AuthenticatedActor actor) {
        List<String> r = jdbc.queryForList("select reservation_requesters from space_rules where organization_id = :o",
                new MapSqlParameterSource("o", actor.organizationId()), String.class);
        String requesters = r.isEmpty() || r.get(0) == null ? "STAFF" : r.get(0);
        if ("ANY_MEMBER".equals(requesters)) {
            return true;
        }
        if ("LEADERS".equals(requesters)) {
            Integer n = jdbc.queryForObject(
                    "select (select count(*) from group_member gm where gm.person_id = :p and gm.organization_id = :o and gm.status = 'ACTIVE'"
                            + " and gm.role in ('LEADER','COLEADER'))"
                            + " + (select count(*) from branch_ministry bm where bm.leader_person_id = :p and bm.organization_id = :o and bm.status = 'ACTIVE')",
                    new MapSqlParameterSource("p", actor.ownerId()).addValue("o", actor.organizationId()), Integer.class);
            return n != null && n > 0;
        }
        return false;
    }

    @Transactional(readOnly = true)
    public List<MySpaceReservationRow> mySpaceReservations(AuthenticatedActor actor) {
        return jdbc.query("select r.id, s.name as space_name, b.name as branch_name, r.title, r.start_at, r.end_at, r.status, r.decision_reason,"
                        + " r.created_at from reservation r join space s on s.id = r.space_id join branch b on b.id = s.branch_id"
                        + " where r.requested_by = :p and r.organization_id = :o order by r.start_at desc",
                new MapSqlParameterSource("p", actor.ownerId()).addValue("o", actor.organizationId()),
                (rs, i) -> new MySpaceReservationRow((UUID) rs.getObject("id"), rs.getString("space_name"), rs.getString("branch_name"),
                        rs.getString("title"), rs.getTimestamp("start_at").toInstant(), rs.getTimestamp("end_at").toInstant(), rs.getString("status"),
                        rs.getString("decision_reason"), rs.getTimestamp("created_at").toInstant()));
    }

    /** Sin recurrencia (ver Javadoc de {@link OpenSpaceRow}) — replica el resto de {@code ReservationService.submit()}
     * tal cual: persona activa, espacio ACTIVE, horario válido y no pasado, duración máxima de la organización, y
     * anti-solape contra CONFIRMED con el buffer del espacio bajo el mismo bloqueo de fila para serializar dos
     * solicitudes simultáneas al mismo hueco. {@code canRequestReservation} se repite aquí aunque el front ya oculte
     * el botón: nunca confiar solo en que la UI lo escondió. */
    @Transactional
    public void requestSpaceReservation(AuthenticatedActor actor, UUID spaceId, String title, Instant startAt, Instant endAt, Integer attendeesEst) {
        List<String> pst = jdbc.queryForList("select status from person where id = :p and organization_id = :o",
                new MapSqlParameterSource("p", actor.ownerId()).addValue("o", actor.organizationId()), String.class);
        if (pst.isEmpty() || !"ACTIVE".equals(pst.get(0))) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, pst.isEmpty() ? "?" : pst.get(0));
        }
        if (!canRequestReservation(actor)) {
            throw new Exceptions("error.reservation.notAllowedFromPortal", HttpStatus.FORBIDDEN);
        }
        if (spaceId == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "espacio");
        }
        List<Map<String, Object>> sp = jdbc.queryForList("select id, branch_id, status, buffer_minutes, requires_approval from space"
                + " where id = :id and organization_id = :o", new MapSqlParameterSource("id", spaceId).addValue("o", actor.organizationId()));
        if (sp.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        Map<String, Object> space = sp.get(0);
        if (!"ACTIVE".equals(space.get("status"))) {
            throw new Exceptions("error.space.notBookable", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String trimmedTitle = trim(title, 150, "título");
        if (trimmedTitle == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "título");
        }
        if (startAt == null || endAt == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "horario");
        }
        if (!endAt.isAfter(startAt)) {
            throw new Exceptions("error.reservation.timeInvalid", HttpStatus.BAD_REQUEST);
        }
        if (startAt.isBefore(Instant.now())) {
            throw new Exceptions("error.reservation.pastStart", HttpStatus.BAD_REQUEST);
        }
        List<Integer> maxRows = jdbc.queryForList("select max_duration_hours from space_rules where organization_id = :o",
                new MapSqlParameterSource("o", actor.organizationId()), Integer.class);
        int maxHours = maxRows.isEmpty() ? 12 : maxRows.get(0);
        Duration exact = Duration.between(startAt, endAt);
        if (exact.toHours() > maxHours || (exact.toHours() == maxHours && exact.toMinutesPart() > 0)) {
            throw new Exceptions("error.reservation.tooLong", HttpStatus.UNPROCESSABLE_ENTITY, maxHours);
        }
        jdbc.queryForList("select id from space where id = :id for update", new MapSqlParameterSource("id", spaceId), UUID.class);
        int bufferMinutes = (Integer) space.get("buffer_minutes");
        Integer overlapCount = jdbc.queryForObject("select count(*) from reservation where space_id = :sp and status = 'CONFIRMED'"
                        + " and (start_at - (:buf || ' minutes')::interval) < :e and (end_at + (:buf || ' minutes')::interval) > :s",
                new MapSqlParameterSource("sp", spaceId).addValue("buf", bufferMinutes).addValue("s", Timestamp.from(startAt))
                        .addValue("e", Timestamp.from(endAt)), Integer.class);
        if (overlapCount != null && overlapCount > 0) {
            throw new Exceptions("error.reservation.overlap", HttpStatus.CONFLICT);
        }
        UUID id = UUID.randomUUID();
        boolean requiresApproval = (Boolean) space.get("requires_approval");
        String status = requiresApproval ? "PENDING" : "CONFIRMED";
        jdbc.update("insert into reservation (id, organization_id, space_id, requested_by, title, source_type, start_at, end_at, attendees_est, status,"
                        + " created_at, created_by) values (:id, :o, :sp, :rb, :t, 'OTHER', :s, :e, :att, :st, :at, :by)",
                new MapSqlParameterSource("id", id).addValue("o", actor.organizationId()).addValue("sp", spaceId).addValue("rb", actor.ownerId())
                        .addValue("t", trimmedTitle).addValue("s", Timestamp.from(startAt)).addValue("e", Timestamp.from(endAt))
                        .addValue("att", attendeesEst).addValue("st", status).addValue("at", Timestamp.from(Instant.now())).addValue("by", actor.ownerId()));
        if (requiresApproval) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("spaceName", space.get("id") == null ? "" : spaceNameOf(spaceId));
            payload.put("title", trimmedTitle);
            UUID reqId = approvals.open(new ApprovalEngine.NewRequest(actor.organizationId(), (UUID) space.get("branch_id"), null, "SPACE_RESERVATION",
                    "RESERVATION", id, actor.ownerId(), null, payload));
            jdbc.update("update reservation set approval_request_id = :r where id = :id", new MapSqlParameterSource("r", reqId).addValue("id", id));
        }
    }

    private String spaceNameOf(UUID spaceId) {
        List<String> n = jdbc.queryForList("select name from space where id = :id", new MapSqlParameterSource("id", spaceId), String.class);
        return n.isEmpty() ? "" : n.get(0);
    }

    /** M24 N4 · bloque MY_WORK (seguimiento 2026-09-26): ver Javadoc de {@link MyWorkResponse} para el porqué de cada
     * exclusión ({@code bank_encrypted}, {@code employerCost}, líneas {@code EMPLOYER_CONTRIBUTION}) y del filtro de
     * periodo (solo {@code payroll_run.status in (APPROVED, PAID, CLOSED)}). Sin fila propia en {@code staff_member}
     * (no es personal contratado) devuelve ambas listas vacías, nunca un error — mismo caso que un miembro sin
     * donaciones en GIVING. */
    @Transactional(readOnly = true)
    public MyWorkResponse myWork(AuthenticatedActor actor) {
        List<MyEmploymentRow> employments = jdbc.query(
                "select s.id, s.position, s.contract_type, s.hire_date, s.contract_end, s.termination_date, s.base_salary, s.currency,"
                        + " s.pay_frequency, b.name as branch_name, m.name as ministry_name, s.status from staff_member s join branch b on b.id = s.branch_id"
                        + " left join ministry m on m.id = s.ministry_id where s.person_id = :p and s.organization_id = :o order by s.hire_date desc",
                new MapSqlParameterSource("p", actor.ownerId()).addValue("o", actor.organizationId()),
                (rs, i) -> new MyEmploymentRow((UUID) rs.getObject("id"), rs.getString("position"), rs.getString("contract_type"),
                        rs.getDate("hire_date").toLocalDate(), rs.getDate("contract_end") == null ? null : rs.getDate("contract_end").toLocalDate(),
                        rs.getDate("termination_date") == null ? null : rs.getDate("termination_date").toLocalDate(), rs.getBigDecimal("base_salary"),
                        rs.getString("currency"), rs.getString("pay_frequency"), rs.getString("branch_name"), rs.getString("ministry_name"),
                        rs.getString("status")));
        List<MyPayslipRow> base = jdbc.query(
                "select pr.id, r.period, pr.gross, pr.deductions, pr.net, pr.worked_days, pr.unpaid_days, pr.payslip_no, r.status as run_status"
                        + " from payroll_record pr join payroll_run r on r.id = pr.run_id join staff_member s on s.id = pr.staff_id"
                        + " where s.person_id = :p and s.organization_id = :o and r.status in ('APPROVED', 'PAID', 'CLOSED')"
                        + " order by r.period desc",
                new MapSqlParameterSource("p", actor.ownerId()).addValue("o", actor.organizationId()),
                (rs, i) -> new MyPayslipRow((UUID) rs.getObject("id"), rs.getString("period"), rs.getBigDecimal("gross"), rs.getBigDecimal("deductions"),
                        rs.getBigDecimal("net"), rs.getBigDecimal("worked_days"), rs.getBigDecimal("unpaid_days"), rs.getString("payslip_no"),
                        rs.getString("run_status"), List.of()));
        List<MyPayslipRow> payslips = base.stream().map(p -> new MyPayslipRow(p.id(), p.period(), p.gross(), p.deductions(), p.net(), p.workedDays(),
                p.unpaidDays(), p.payslipNo(), p.runStatus(), payslipLines(p.id()))).toList();
        return new MyWorkResponse(employments, payslips);
    }

    private List<MyPayslipLineRow> payslipLines(UUID recordId) {
        return jdbc.query("select concept_name, kind, amount from payroll_record_line where record_id = :id and kind in ('EARNING', 'DEDUCTION')"
                        + " order by kind desc, concept_name",
                new MapSqlParameterSource("id", recordId),
                (rs, i) -> new MyPayslipLineRow(rs.getString("concept_name"), rs.getString("kind"), rs.getBigDecimal("amount")));
    }

    private void promoteOneWaitlisted(UUID orgId, UUID eventId) {
        List<UUID> next = jdbc.query("select id from event_registration where event_id = :id and status = 'WAITLISTED' order by created_at limit 1",
                new MapSqlParameterSource("id", eventId), (rs, i) -> (UUID) rs.getObject(1));
        if (next.isEmpty()) {
            return;
        }
        UUID regId = next.get(0);
        jdbc.update("update event_registration set status = 'REGISTERED', updated_at = :at, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(Instant.now())).addValue("id", regId));
        issueTicket(regId);
        List<Map<String, Object>> info = jdbc.queryForList("select r.person_id, e.name as event_name, e.id as event_id from event_registration r"
                        + " join org_event e on e.id = r.event_id where r.id = :id",
                new MapSqlParameterSource("id", regId));
        if (!info.isEmpty()) {
            UUID personId = (UUID) info.get(0).get("person_id");
            String eventName = (String) info.get(0).get("event_name");
            notifications.toPersons(NotificationType.EVENT_WAITLIST_PROMOTED, orgId, List.of(personId), Map.of("event", eventName),
                    "/portal/events", null);
        }
    }

    /** Bloquea la propia fila de {@code shift_assignment} (anti-IDOR: filtra por {@code person_id} en el WHERE, nunca lo verifica después). */
    private Map<String, Object> lockOwnShift(AuthenticatedActor actor, UUID assignmentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select a.id, a.status, p.plan_date, sl.start_time, p.branch_id, m.name as ministry_name,"
                + " bm.leader_person_id " + MY_SHIFT_JOIN + " where a.id = :id and a.person_id = :p and p.organization_id = :o for update of a",
                new MapSqlParameterSource("id", assignmentId).addValue("p", actor.ownerId()).addValue("o", actor.organizationId()));
        if (rows.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return rows.get(0);
    }

    /** Zona horaria de la sede (para calcular el instante exacto del turno), igual criterio que {@code VolunteerSupport.zoneOf}. */
    private ZoneId zoneOf(UUID branchId) {
        List<String> z = jdbc.queryForList("select coalesce(b.timezone, o.timezone) from branch b join organization o on o.id = b.organization_id where b.id = :b",
                new MapSqlParameterSource("b", branchId), String.class);
        try {
            return z.isEmpty() || z.get(0) == null ? ZoneId.of("America/Lima") : ZoneId.of(z.get(0));
        } catch (RuntimeException e) {
            return ZoneId.of("America/Lima");
        }
    }

    private static String trim(String s, int max, String label) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        if (t.length() > max) {
            throw new Exceptions("error.common.tooLong", HttpStatus.BAD_REQUEST, label, max);
        }
        return t;
    }

    private static boolean isMinor(LocalDate birthDate) {
        return birthDate != null && Period.between(birthDate, LocalDate.now()).getYears() < 18;
    }

    @Transactional(readOnly = true)
    public HomeResponse home(AuthenticatedActor actor) {
        var s = settings.effectiveForMember(actor.organizationId(), actor.activeBranchId());
        var blocks = s.homeBlocks().stream().filter(b -> b.enabled()).sorted((a, b) -> Integer.compare(a.order(), b.order())).toList();
        boolean en = "en".equalsIgnoreCase(LocaleContextHolder.getLocale().getLanguage());
        String text = en && s.welcomeTextEn() != null && !s.welcomeTextEn().isBlank() ? s.welcomeTextEn() : s.welcomeTextEs();
        return new HomeResponse(text, blocks, s.directoryEnabled());
    }
}
