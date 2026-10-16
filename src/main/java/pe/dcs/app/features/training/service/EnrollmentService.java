package pe.dcs.app.features.training.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
import pe.dcs.app.features.training.dto.TrainingDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M13 · Matrículas: ENROLLED → APPROVED (nota ≥ aprobación y asistencia mínima si aplica) | FAILED | WITHDRAWN. Prerrequisito entre
 * niveles de la MISMA malla, buscando en toda la organización [V8]; excepción con motivo, solo ORG_ADMIN/ORG_BRANCH_ADMIN [T04].
 */
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private static final String MODULE = TrainingSupport.MODULE;
    private static final String ENTITY = "Enrollment";
    private static final Set<String> RESOLVED = Set.of("APPROVED", "FAILED", "WITHDRAWN");
    private static final long LATE_EDIT_DAYS = 30;

    private final NamedParameterJdbcTemplate jdbc;
    private final TrainingSupport support;
    private final CurriculumService curricula;
    private final CourseClassService classes;
    private final GradeScaleService gradeScale;
    private final TrainingCertificateService certificates;
    private final NotificationService notifications;
    private final AuthorizationService authz;
    private final AuditService audit;
    private final Clock clock;

    record ERow(UUID id, UUID orgId, UUID branchId, UUID personId, UUID classId, String status, BigDecimal finalGrade, Instant gradedAt, long version) {
    }

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public PageResponse<TrainingDtos.EnrollmentSummary> search(AccessScope scope, TrainingDtos.EnrollmentSearch req) {
        TrainingDtos.EnrollmentSearch.EnrollmentFilters f = req == null || req.filters() == null
                ? new TrainingDtos.EnrollmentSearch.EnrollmentFilters(null, null, null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder("e.organization_id = :org");
        ps.addValue("org", scope.organizationId());
        if (!scope.allBranches()) {
            List<UUID> ids = scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
            ps.addValue("scopeBranches", ids);
            w.append(" and e.branch_id in (:scopeBranches)");
        }
        if (scope.role() == RoleType.ORG_USER) {
            UUID me = scope.personId() == null ? new UUID(0, 0) : scope.personId();
            ps.addValue("meOwn", me);
            w.append(" and t.teacher_person_id = :meOwn");
        }
        if (f.personId() != null) {
            w.append(" and e.person_id = :fp");
            ps.addValue("fp", f.personId());
        }
        if (f.classId() != null) {
            w.append(" and e.class_id = :fc");
            ps.addValue("fc", f.classId());
        }
        if (f.courseId() != null) {
            w.append(" and t.course_id = :fco");
            ps.addValue("fco", f.courseId());
        }
        if (TrainingSupport.hasText(f.status())) {
            String s = f.status().trim().toUpperCase();
            w.append(" and e.status = :fs");
            ps.addValue("fs", s);
        }
        String from = " from enrollment e join course_class t on t.id = e.class_id join course k on k.id = t.course_id join person p on p.id = e.person_id"
                + " join branch b on b.id = e.branch_id where ";
        Long total = jdbc.queryForObject("select count(*)" + from + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<TrainingDtos.EnrollmentSummary> rows = jdbc.query(SUMMARY + from + w + " order by e.enrolled_at desc limit :lim offset :off", ps, (rs, i) -> summary(rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    private static final String SUMMARY = "select e.id, e.person_id, trim(p.first_name || ' ' || p.last_name) as person_name, e.class_id, k.name as course_name,"
            + " k.order_num, e.branch_id, b.name as branch_name, e.status, e.final_grade, e.enrolled_at, e.resolved_at, e.version";

    private TrainingDtos.EnrollmentSummary summary(java.sql.ResultSet rs) throws java.sql.SQLException {
        BigDecimal g = rs.getBigDecimal("final_grade");
        java.sql.Timestamp resolved = rs.getTimestamp("resolved_at");
        return new TrainingDtos.EnrollmentSummary((UUID) rs.getObject("id"), (UUID) rs.getObject("person_id"), rs.getString("person_name"), (UUID) rs.getObject("class_id"),
                rs.getString("course_name"), (Integer) rs.getObject("order_num"), (UUID) rs.getObject("branch_id"), rs.getString("branch_name"), rs.getString("status"),
                g == null ? null : g.toPlainString(), rs.getTimestamp("enrolled_at").toInstant(), resolved == null ? null : resolved.toInstant(), rs.getLong("version"));
    }

    @Transactional(readOnly = true)
    public TrainingDtos.EnrollmentResponse get(AccessScope scope, UUID id) {
        return build(load(scope, id));
    }

    ERow load(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id).addValue("org", scope.organizationId());
        StringBuilder w = new StringBuilder("e.id = :id and e.organization_id = :org");
        if (!scope.allBranches()) {
            List<UUID> ids = scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
            ps.addValue("scopeBranches", ids);
            w.append(" and e.branch_id in (:scopeBranches)");
        }
        return jdbc.query("select e.* from enrollment e where " + w, ps, (rs, i) -> row(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private ERow lock(AccessScope scope, UUID id) {
        load(scope, id);
        return jdbc.query("select * from enrollment where id = :id for update", new MapSqlParameterSource("id", id), (rs, i) -> row(rs)).get(0);
    }

    private static ERow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Timestamp graded = rs.getTimestamp("graded_at");
        return new ERow((UUID) rs.getObject("id"), (UUID) rs.getObject("organization_id"), (UUID) rs.getObject("branch_id"), (UUID) rs.getObject("person_id"),
                (UUID) rs.getObject("class_id"), rs.getString("status"), rs.getBigDecimal("final_grade"), graded == null ? null : graded.toInstant(), rs.getLong("version"));
    }

    private TrainingDtos.EnrollmentResponse build(ERow e) {
        TrainingDtos.EnrollmentSummary s = jdbc.query(SUMMARY + " from enrollment e join course_class t on t.id = e.class_id join course k on k.id = t.course_id"
                + " join branch b on b.id = e.branch_id join person p on p.id = e.person_id where e.id = :id", new MapSqlParameterSource("id", e.id()), (rs, i) -> summary(rs)).get(0);
        Map<String, Object> extra = jdbc.queryForMap("select status_reason, override_reason, overridden_by from enrollment where id = :id", new MapSqlParameterSource("id", e.id()));
        UUID overriddenBy = (UUID) extra.get("overridden_by");
        TrainingDtos.CertificateResponse cert = certificates.currentOrNull(e.id());
        return new TrainingDtos.EnrollmentResponse(s, (String) extra.get("status_reason"), (String) extra.get("override_reason"),
                overriddenBy == null ? null : support.personName(overriddenBy), cert);
    }

    // ---------------------------------------------------------------- matricular

    @Transactional
    public TrainingDtos.EnrollmentResponse enroll(AuthenticatedActor actor, AccessScope scope, TrainingDtos.EnrollRequest r) {
        authz.require(actor, MODULE, Action.C);
        if (r == null || r.personId() == null || r.classId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "persona y dictado");
        }
        support.assertPersonActive(scope.organizationId(), r.personId());
        CourseClassService.ClassRow c = classes.classRow(scope, r.classId());
        if (!"PLANNED".equals(c.status()) && !"IN_PROGRESS".equals(c.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, c.status());
        }
        CurriculumService.CourseRow course = curricula.course(scope, c.courseId());
        Integer approvedAlready = jdbc.queryForObject("select count(*) from enrollment e join course_class t on t.id = e.class_id"
                        + " where e.person_id = :p and t.course_id = :co and e.status = 'APPROVED'",
                new MapSqlParameterSource("p", r.personId()).addValue("co", c.courseId()), Integer.class);
        if (approvedAlready != null && approvedAlready > 0) {
            throw new Exceptions("error.training.alreadyApproved", HttpStatus.UNPROCESSABLE_ENTITY);                     // [V7]
        }
        boolean overrideUsed = false;
        if (course.curriculumId() != null && course.order() != null && course.order() > 1) {
            boolean has = jdbc.queryForObject("select count(*) from enrollment e join course_class t on t.id = e.class_id join course k on k.id = t.course_id"
                            + " where e.person_id = :p and k.curriculum_id = :cur and k.order_num = :ord and e.status = 'APPROVED'",
                    new MapSqlParameterSource("p", r.personId()).addValue("cur", course.curriculumId()).addValue("ord", course.order() - 1), Integer.class) > 0;
            if (!has) {                                                                                                  // [V8]
                if (!TrainingSupport.hasText(r.overrideReason())) {
                    String required = jdbc.queryForObject("select name from course where curriculum_id = :cur and order_num = :ord",
                            new MapSqlParameterSource("cur", course.curriculumId()).addValue("ord", course.order() - 1), String.class);
                    throw new Exceptions("error.training.prerequisite", HttpStatus.UNPROCESSABLE_ENTITY, required);
                }
                authz.require(actor, MODULE, Action.O);
                if (scope.role() == RoleType.ORG_USER) {
                    throw new Exceptions("error.training.overrideDenied", HttpStatus.FORBIDDEN);                         // [T04]
                }
                overrideUsed = true;
            }
        }
        int enrolled = classes.enrolledCount(r.classId());
        if (enrolled >= c.capacity()) {
            throw new Exceptions("error.training.classFull", HttpStatus.UNPROCESSABLE_ENTITY);                          // [V9]
        }
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(clock.instant());
        try {
            jdbc.update("insert into enrollment (id, organization_id, branch_id, person_id, class_id, status, override_reason, overridden_by, enrolled_at, created_by)"
                            + " values (:id, :o, :b, :p, :c, 'ENROLLED', :or, :ob, :at, :by)",
                    new MapSqlParameterSource("id", id).addValue("o", c.orgId()).addValue("b", c.branchId()).addValue("p", r.personId()).addValue("c", r.classId())
                            .addValue("or", overrideUsed ? TrainingSupport.trim(r.overrideReason(), 300, "motivo") : null)
                            .addValue("ob", overrideUsed ? scope.personId() : null).addValue("at", now).addValue("by", actor.ownerId()));
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.training.alreadyEnrolled", HttpStatus.CONFLICT);                                 // [V7]
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("course", course.name());
        d.put("override", overrideUsed);
        audit.record(new AuditService.Command(MODULE, "ENROLL", ENTITY, id, c.orgId(), c.branchId(), d));
        return get(scope, id);
    }

    // ---------------------------------------------------------------- nota

    /** [V10] la nota debe estar dentro de la escala; [V11] editarla más de 30 días después de COMPLETED exige motivo (queda en auditoría). */
    @Transactional
    public TrainingDtos.EnrollmentResponse grade(AuthenticatedActor actor, AccessScope scope, UUID id, TrainingDtos.GradeRequest r) {
        authz.require(actor, MODULE, Action.E);
        ERow e = lock(scope, id);
        if ("WITHDRAWN".equals(e.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, e.status());
        }
        GradeScaleService.Scale scale = gradeScale.current(e.orgId());
        BigDecimal grade = number(r == null ? null : r.grade());
        if (grade.compareTo(scale.min()) < 0 || grade.compareTo(scale.max()) > 0) {
            throw new Exceptions("error.training.gradeOutOfScale", HttpStatus.UNPROCESSABLE_ENTITY, scale.min().toPlainString(), scale.max().toPlainString());
        }
        List<java.sql.Timestamp> completedRows = jdbc.query("select completed_at from course_class where id = (select class_id from enrollment where id = :id)",
                new MapSqlParameterSource("id", id), (rs, i) -> rs.getTimestamp(1));
        java.sql.Timestamp completedAt = completedRows.isEmpty() ? null : completedRows.get(0);
        boolean late = completedAt != null && Duration.between(completedAt.toInstant(), clock.instant()).toDays() > LATE_EDIT_DAYS;
        if (late && !TrainingSupport.hasText(r.lateReason())) {
            throw new Exceptions("error.training.statusReasonRequired", HttpStatus.BAD_REQUEST);
        }
        jdbc.update("update enrollment set final_grade = :g, graded_at = :at, version = version + 1 where id = :id",
                new MapSqlParameterSource("g", grade).addValue("at", Timestamp.from(clock.instant())).addValue("id", id));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("grade", grade.toPlainString());
        if (late) {
            d.put("lateReason", TrainingSupport.trim(r.lateReason(), 300, "motivo"));
        }
        audit.record(new AuditService.Command(MODULE, "GRADE", ENTITY, id, e.orgId(), e.branchId(), d));
        return get(scope, id);
    }

    // ---------------------------------------------------------------- estado

    /** ENROLLED → APPROVED | FAILED | WITHDRAWN. FAILED/WITHDRAWN exigen motivo; APPROVED exige nota ≥ aprobación y asistencia mínima, y emite certificado. */
    @Transactional
    public TrainingDtos.EnrollmentResponse status(AuthenticatedActor actor, AccessScope scope, UUID id, TrainingDtos.StatusRequest r) {
        authz.require(actor, MODULE, Action.S);
        ERow e = lock(scope, id);
        if (!"ENROLLED".equals(e.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, e.status());
        }
        String status = r == null || !TrainingSupport.hasText(r.status()) ? null : r.status().trim().toUpperCase();
        if (status == null || !RESOLVED.contains(status)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
        }
        String reason = TrainingSupport.trim(r.reason(), 300, "motivo");
        if (("FAILED".equals(status) || "WITHDRAWN".equals(status)) && reason == null) {
            throw new Exceptions("error.training.statusReasonRequired", HttpStatus.BAD_REQUEST);
        }
        CourseClassService.ClassRow c = classes.classRow(scope, e.classId());
        CurriculumService.CourseRow course = curricula.course(scope, c.courseId());
        if ("APPROVED".equals(status)) {
            if (e.finalGrade() == null) {
                throw new Exceptions("error.training.resultRequired", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            BigDecimal pass = course.passGrade() != null ? course.passGrade() : gradeScale.current(e.orgId()).pass();
            if (e.finalGrade().compareTo(pass) < 0) {
                throw new Exceptions("error.training.gradeBelowPass", HttpStatus.UNPROCESSABLE_ENTITY, pass.toPlainString());
            }
            if (course.minAttendancePct() != null) {
                int pct = classes.attendancePct(e.classId(), e.personId());
                if (pct < course.minAttendancePct()) {
                    throw new Exceptions("error.training.attendanceBelowMin", HttpStatus.UNPROCESSABLE_ENTITY, pct, course.minAttendancePct());
                }
            }
        }
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("update enrollment set status = :s, status_reason = :r, resolved_at = :at, version = version + 1 where id = :id",
                new MapSqlParameterSource("s", status).addValue("r", reason).addValue("at", now).addValue("id", id));
        audit.record(new AuditService.Command(MODULE, "STATUS", ENTITY, id, e.orgId(), e.branchId(), Map.of("status", status)));
        if ("APPROVED".equals(status)) {
            certificates.issue(scope, id);
            notifications.toPersons(NotificationType.TRAINING_CERTIFICATE_ISSUED, e.orgId(), List.of(e.personId()), Map.of("course", course.name()),
                    "/app/training/enrollments/" + id, null);
        } else {
            notifications.toPersons(NotificationType.TRAINING_ENROLLMENT_RESULT, e.orgId(), List.of(e.personId()),
                    Map.of("course", course.name(), "status", status), "/app/training/enrollments/" + id, null);
        }
        return get(scope, id);
    }

    private static BigDecimal number(String s) {
        if (s == null || s.isBlank()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "nota");
        }
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "nota");
        }
    }
}
