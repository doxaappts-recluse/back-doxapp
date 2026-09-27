package pe.dcs.app.features.volunteer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.attendance.dto.AttendanceDtos;
import pe.dcs.app.features.attendance.service.AttendanceService;
import pe.dcs.app.features.ministry.service.MinistrySupport;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
import pe.dcs.app.features.volunteer.dto.VolunteerDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M11b · Asignación de personas a los turnos de un plan. [V5][V6] elegibilidad y verificación (se reusa {@link MinistrySupport}) · [V9] sin cruce de horario con
 * otro turno del mismo día (409, no admite override) · [V10] respeta la no disponibilidad salvo override con motivo · [V11] tope mensual salvo override ·
 * [V12] rechazar dentro de la ventana de bloqueo exige motivo y avisa a quien lidera · [V14] el rechazo devuelve sugerencias de reemplazo.
 */
@Service
@RequiredArgsConstructor
public class ShiftAssignmentService {

    static final String MODULE = VolunteerSupport.MODULE;
    private static final int SUGGEST_LIMIT = 10;

    private final NamedParameterJdbcTemplate jdbc;
    private final VolunteerSupport support;
    private final ServicePlanService plans;
    private final VolunteerRulesService rules;
    private final AttendanceService attendance;
    private final AuthorizationService authz;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    private record AssignmentRow(UUID id, UUID slotId, UUID personId, String status, long version) {
    }

    private AssignmentRow lockAssignment(UUID slotId, UUID assignmentId) {
        return jdbc.query("select id, slot_id, person_id, status, version from shift_assignment where id = :id and slot_id = :s for update",
                new MapSqlParameterSource("id", assignmentId).addValue("s", slotId),
                (rs, i) -> new AssignmentRow((UUID) rs.getObject(1), (UUID) rs.getObject(2), (UUID) rs.getObject(3), rs.getString(4), rs.getLong(5))).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    // ---------------------------------------------------------------- disponibilidad, cruce y tope mensual

    private boolean isAvailable(UUID personId, LocalDate date) {
        int dow = date.getDayOfWeek().getValue();
        Integer n = jdbc.queryForObject("select count(*) from availability where person_id = :p and ((recurrence = 'ONCE' and :d between from_date and coalesce(to_date, from_date))"
                        + " or (recurrence = 'WEEKLY' and :d >= from_date and (to_date is null or :d <= to_date) and day_of_week = :dw))",
                new MapSqlParameterSource("p", personId).addValue("d", java.sql.Date.valueOf(date)).addValue("dw", dow), Integer.class);
        return n == null || n == 0;
    }

    private boolean overlaps(UUID personId, LocalDate date, LocalTime start, LocalTime end, UUID exceptSlotId) {
        Integer n = jdbc.queryForObject("select count(*) from shift_assignment a join shift_slot sl on sl.id = a.slot_id join service_plan pl on pl.id = sl.plan_id"
                        + " where a.person_id = :p and pl.plan_date = :d and a.status in ('PROPOSED','CONFIRMED','SERVED') and sl.start_time < :et and sl.end_time > :st"
                        + " and (cast(:x as uuid) is null or sl.id <> :x)",
                new MapSqlParameterSource("p", personId).addValue("d", java.sql.Date.valueOf(date)).addValue("st", java.sql.Time.valueOf(start))
                        .addValue("et", java.sql.Time.valueOf(end)).addValue("x", exceptSlotId), Integer.class);
        return n != null && n > 0;
    }

    private int monthlyCount(UUID personId, LocalDate date) {
        Integer n = jdbc.queryForObject("select count(*) from shift_assignment a join shift_slot sl on sl.id = a.slot_id join service_plan pl on pl.id = sl.plan_id"
                        + " where a.person_id = :p and a.status in ('PROPOSED','CONFIRMED','SERVED') and date_trunc('month', pl.plan_date) = date_trunc('month', cast(:d as date))",
                new MapSqlParameterSource("p", personId).addValue("d", java.sql.Date.valueOf(date)), Integer.class);
        return n == null ? 0 : n;
    }

    // ---------------------------------------------------------------- asignar (manual)

    @Transactional
    public VolunteerDtos.PlanResponse assign(AuthenticatedActor actor, AccessScope scope, UUID planId, UUID slotId, VolunteerDtos.AssignRequest r) {
        authz.require(actor, MODULE, Action.C);
        VolunteerSupport.PlanRow p = support.lockPlan(scope, planId);
        if ("CLOSED".equals(p.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, p.status());
        }
        VolunteerSupport.SlotRow s = support.loadSlot(planId, slotId);
        if (!support.leads(scope, s.branchMinistryId())) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        if (r == null || r.personId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "persona");
        }
        MinistrySupport.BranchMinistryRow bm = support.ministry().loadBmRaw(s.branchMinistryId());
        MinistrySupport.PersonRef person = support.ministry().activePerson(scope, r.personId());
        LocalDate today = support.ministry().orgToday(p.orgId());
        support.ministry().assertEligible(bm, person, false, today);                                                                // [V5][V6]
        boolean override = Boolean.TRUE.equals(r.overrideChecks());
        String overrideReason = MinistrySupport.trim(r.overrideReason(), 200, "motivo");
        if (override && overrideReason == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "motivo");
        }
        if (overlaps(person.id(), p.planDate(), s.start(), s.end(), null)) {
            throw new Exceptions("error.shift.overlap", HttpStatus.CONFLICT);                                                       // [V9]
        }
        if (!isAvailable(person.id(), p.planDate()) && !override) {
            throw new Exceptions("error.shift.unavailable", HttpStatus.UNPROCESSABLE_ENTITY);                                       // [V10]
        }
        int max = rules.get(p.orgId()).maxShiftsPerMonth();
        if (monthlyCount(person.id(), p.planDate()) >= max && !override) {
            throw new Exceptions("error.shift.monthlyLimit", HttpStatus.UNPROCESSABLE_ENTITY, max);                                 // [V11]
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into shift_assignment (id, slot_id, person_id, status, created_at, created_by) values (:id, :s, :p, 'PROPOSED', :at, :by)",
                    new MapSqlParameterSource("id", id).addValue("s", slotId).addValue("p", person.id()).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()));
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.shift.assignmentExists", HttpStatus.CONFLICT);
        }
        Map<String, Object> d = new java.util.LinkedHashMap<>();
        d.put("person", person.id().toString());
        d.put("override", override);
        audit.record(new AuditService.Command(MODULE, "ASSIGN", "ShiftAssignment", id, p.orgId(), p.branchId(), d));
        if ("PUBLISHED".equals(p.status())) {
            notifications.toPersons(NotificationType.SHIFT_ASSIGNED, p.orgId(), List.of(person.id()),
                    Map.of("ministry", bm.ministryName(), "date", p.planDate().toString(), "time", s.start().toString()), "/app/volunteer-scheduling",
                    "SHIFT_ASSIGN:" + id);
        }
        return plans.getRaw(planId);
    }

    @Transactional
    public VolunteerDtos.PlanResponse unassign(AuthenticatedActor actor, AccessScope scope, UUID planId, UUID slotId, UUID assignmentId) {
        authz.require(actor, MODULE, Action.D);
        VolunteerSupport.PlanRow p = support.lockPlan(scope, planId);
        VolunteerSupport.SlotRow s = support.loadSlot(planId, slotId);
        if (!support.leads(scope, s.branchMinistryId())) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        AssignmentRow a = lockAssignment(slotId, assignmentId);
        if (!Set.of("PROPOSED", "DECLINED").contains(a.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, a.status());
        }
        jdbc.update("delete from shift_assignment where id = :id", new MapSqlParameterSource("id", assignmentId));
        audit.record(new AuditService.Command(MODULE, "UNASSIGN", "ShiftAssignment", assignmentId, p.orgId(), p.branchId(), Map.of("id", assignmentId.toString())));
        return plans.getRaw(planId);
    }

    // ---------------------------------------------------------------- confirmar / rechazar (a cargo de quien coordina; el autoservicio llega en M24)

    @Transactional
    public VolunteerDtos.PlanResponse confirm(AuthenticatedActor actor, AccessScope scope, UUID planId, UUID slotId, UUID assignmentId) {
        authz.require(actor, MODULE, Action.E);
        VolunteerSupport.PlanRow p = support.lockPlan(scope, planId);
        VolunteerSupport.SlotRow s = support.loadSlot(planId, slotId);
        if (!support.leads(scope, s.branchMinistryId())) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        AssignmentRow a = lockAssignment(slotId, assignmentId);
        if (!"PROPOSED".equals(a.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, a.status());
        }
        jdbc.update("update shift_assignment set status = 'CONFIRMED', responded_at = :at, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("id", assignmentId));
        audit.record(new AuditService.Command(MODULE, "CONFIRM", "ShiftAssignment", assignmentId, p.orgId(), p.branchId(), Map.of("id", assignmentId.toString())));
        return plans.getRaw(planId);
    }

    /** [V12] Dentro de la ventana de bloqueo exige motivo; siempre avisa a quien lidera y deja lista la vacante con sugerencias [V14]. */
    @Transactional
    public VolunteerDtos.PlanResponse decline(AuthenticatedActor actor, AccessScope scope, UUID planId, UUID slotId, UUID assignmentId, VolunteerDtos.DecideRequest r) {
        authz.require(actor, MODULE, Action.E);
        VolunteerSupport.PlanRow p = support.lockPlan(scope, planId);
        VolunteerSupport.SlotRow s = support.loadSlot(planId, slotId);
        if (!support.leads(scope, s.branchMinistryId())) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        AssignmentRow a = lockAssignment(slotId, assignmentId);
        if (!Set.of("PROPOSED", "CONFIRMED").contains(a.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, a.status());
        }
        MinistrySupport.BranchMinistryRow bm = support.ministry().loadBmRaw(s.branchMinistryId());
        ZoneId zone = support.zoneOf(p.branchId());
        Instant shiftStart = p.planDate().atTime(s.start()).atZone(zone).toInstant();
        long hoursLeft = Duration.between(clock.instant(), shiftStart).toHours();
        int lockHours = rules.get(p.orgId()).declineLockHours();
        String reason = MinistrySupport.trim(r == null ? null : r.reason(), 300, "motivo");
        if (hoursLeft < lockHours && reason == null) {
            throw new Exceptions("error.shift.declineReason", HttpStatus.UNPROCESSABLE_ENTITY);                                     // [V12]
        }
        jdbc.update("update shift_assignment set status = 'DECLINED', decline_reason = :r, responded_at = :at, version = version + 1 where id = :id",
                new MapSqlParameterSource("r", reason).addValue("at", Timestamp.from(clock.instant())).addValue("id", assignmentId));
        if (bm.leaderId() != null) {
            notifications.toPersons(NotificationType.SHIFT_DECLINED, p.orgId(), List.of(bm.leaderId()),
                    Map.of("ministry", bm.ministryName(), "date", p.planDate().toString(), "time", s.start().toString()), "/app/volunteer-scheduling", null);
        }
        audit.record(new AuditService.Command(MODULE, "DECLINE", "ShiftAssignment", assignmentId, p.orgId(), p.branchId(), Map.of("reason", reason == null ? "" : reason)));
        return plans.getRaw(planId);
    }

    // ---------------------------------------------------------------- sugerencias [V4][T04]

    /** Candidatos elegibles para un turno, ordenados de forma determinista: menos turnos este mes primero, luego por nombre. No incluye a quien ya está en el turno. */
    @Transactional(readOnly = true)
    public List<VolunteerDtos.Candidate> suggest(AccessScope scope, UUID planId, UUID slotId) {
        VolunteerSupport.PlanRow p = support.loadPlan(scope, planId);
        VolunteerSupport.SlotRow s = support.loadSlot(planId, slotId);
        MinistrySupport.BranchMinistryRow bm = support.ministry().loadBmRaw(s.branchMinistryId());
        LocalDate today = support.ministry().orgToday(p.orgId());
        List<UUID> already = jdbc.queryForList("select person_id from shift_assignment where slot_id = :s and status in ('PROPOSED','CONFIRMED','SERVED')",
                new MapSqlParameterSource("s", slotId), UUID.class);
        List<Object[]> candidates = jdbc.query("select distinct a.person_id, trim(pe.first_name || ' ' || pe.last_name) as pname, pe.last_name, pe.first_name"
                        + " from ministry_assignment a join person pe on pe.id = a.person_id where a.branch_ministry_id = :bm and a.status = 'ACTIVE'"
                        + (s.positionId() == null ? "" : " and a.position_id = :po"),
                s.positionId() == null ? new MapSqlParameterSource("bm", s.branchMinistryId()) : new MapSqlParameterSource("bm", s.branchMinistryId()).addValue("po", s.positionId()),
                (rs, i) -> new Object[]{rs.getObject(1), rs.getString(2), rs.getString(3), rs.getString(4)});
        List<VolunteerDtos.Candidate> out = new ArrayList<>();
        for (Object[] c : candidates) {
            UUID personId = (UUID) c[0];
            if (already.contains(personId)) {
                continue;
            }
            MinistrySupport.PersonRef person;
            try {
                person = support.ministry().activePersonOrg(p.orgId(), personId);
            } catch (Exceptions e) {
                continue;
            }
            String screening = support.ministry().screeningState(personId, bm.requiresScreening(), bm.screeningType(), today);
            if ((bm.adultOnly() && person.minor(today)) || (!"OK".equals(screening) && !"NOT_REQUIRED".equals(screening))) {
                continue;                                                                                                            // [V5][V6]
            }
            boolean overlap = overlaps(personId, p.planDate(), s.start(), s.end(), null);
            if (overlap) {
                continue;                                                                                                            // [V9]
            }
            boolean available = isAvailable(personId, p.planDate());
            int count = monthlyCount(personId, p.planDate());
            boolean withinLimit = count < rules.get(p.orgId()).maxShiftsPerMonth();
            out.add(new VolunteerDtos.Candidate(personId, (String) c[1], count, available, withinLimit, screening));
        }
        out.sort(java.util.Comparator.comparingInt(VolunteerDtos.Candidate::shiftsThisMonth)
                .thenComparing(cd -> !cd.available())
                .thenComparing(cd -> !cd.withinLimit())
                .thenComparing(VolunteerDtos.Candidate::personName, String.CASE_INSENSITIVE_ORDER));
        return out.size() > SUGGEST_LIMIT ? out.subList(0, SUGGEST_LIMIT) : out;
    }

    // ---------------------------------------------------------------- servido / no asistió (asistencia núcleo M09, contexto SHIFT)

    @Transactional
    public VolunteerDtos.PlanResponse serve(AuthenticatedActor actor, AccessScope scope, UUID planId, UUID slotId, UUID assignmentId, VolunteerDtos.ServeRequest r) {
        authz.require(actor, MODULE, Action.S);
        VolunteerSupport.PlanRow p = support.lockPlan(scope, planId);
        if (!Set.of("PUBLISHED", "CLOSED").contains(p.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, p.status());
        }
        if (p.planDate().isAfter(support.ministry().orgToday(p.orgId()))) {
            throw new Exceptions("error.shift.notYet", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        VolunteerSupport.SlotRow s = support.loadSlot(planId, slotId);
        if (!support.leads(scope, s.branchMinistryId())) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        AssignmentRow a = lockAssignment(slotId, assignmentId);
        if (!Set.of("PROPOSED", "CONFIRMED").contains(a.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, a.status());
        }
        String status = r == null || r.status() == null ? "" : r.status().trim().toUpperCase();
        if (!Set.of("SERVED", "NO_SHOW").contains(status)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
        }
        int durationMin = (int) Duration.between(s.start(), s.end()).toMinutes();
        UUID sessionId = attendance.ensureContextSession(p.orgId(), p.branchId(), "SHIFT", slotId, "Turno " + p.planDate(), p.planDate(), s.start(), Math.max(15, durationMin), actor.ownerId());
        attendance.record(actor, scope, sessionId, new AttendanceDtos.RecordRequest(a.personId(), "SERVED".equals(status) ? "PRESENT" : "ABSENT", "MANUAL"));
        jdbc.update("update shift_assignment set status = :st, responded_at = coalesce(responded_at, :at), version = version + 1 where id = :id",
                new MapSqlParameterSource("st", status).addValue("at", Timestamp.from(clock.instant())).addValue("id", assignmentId));
        audit.record(new AuditService.Command(MODULE, status, "ShiftAssignment", assignmentId, p.orgId(), p.branchId(), Map.of("person", a.personId().toString())));
        return plans.getRaw(planId);
    }

    /** Horas de servicio de una persona (turnos SERVED) en un rango; para su ficha. */
    @Transactional(readOnly = true)
    public double hoursServed(AccessScope scope, UUID personId, LocalDate from, LocalDate to) {
        support.ministry().activePerson(scope, personId);
        Double minutes = jdbc.queryForObject("select coalesce(sum(extract(epoch from (sl.end_time - sl.start_time)) / 60), 0) from shift_assignment a"
                        + " join shift_slot sl on sl.id = a.slot_id join service_plan pl on pl.id = sl.plan_id"
                        + " where a.person_id = :p and a.status = 'SERVED' and pl.plan_date between :f and :t",
                new MapSqlParameterSource("p", personId).addValue("f", java.sql.Date.valueOf(from)).addValue("t", java.sql.Date.valueOf(to)), Double.class);
        return minutes == null ? 0 : Math.round(minutes / 6) / 10.0;
    }
}
