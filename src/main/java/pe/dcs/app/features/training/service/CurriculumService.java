package pe.dcs.app.features.training.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.training.dto.TrainingDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M13 · Malla curricular (por nivel) y sus cursos vinculados. Solo el administrador de la organización crea/edita mallas y cursos
 * vinculados [N2]; una sola malla ACTIVE por organización, activar retira la anterior sin tocar su historial [V1-V4].
 */
@Service
@RequiredArgsConstructor
public class CurriculumService {

    private static final String MODULE = TrainingSupport.MODULE;
    private static final String ENTITY = "Curriculum";
    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "RETIRED");

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authz;
    private final AuditService audit;
    private final Clock clock;

    record CRow(UUID id, UUID orgId, String name, String status, long version) {
    }

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public List<TrainingDtos.CurriculumSummary> list(AccessScope scope, String status) {
        MapSqlParameterSource ps = new MapSqlParameterSource("o", scope.organizationId());
        StringBuilder w = new StringBuilder("c.organization_id = :o");
        if (TrainingSupport.hasText(status)) {
            String s = status.trim().toUpperCase();
            if (!STATUSES.contains(s)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
            }
            w.append(" and c.status = :s");
            ps.addValue("s", s);
        }
        return jdbc.query("select c.id, c.name, c.description, c.status, c.created_at, c.version,"
                        + " (select count(*) from course k where k.curriculum_id = c.id) as courses,"
                        + " (select count(*) from course_class cc join course k on k.id = cc.course_id where k.curriculum_id = c.id) as classes"
                        + " from curriculum c where " + w + " order by c.created_at desc", ps,
                (rs, i) -> new TrainingDtos.CurriculumSummary((UUID) rs.getObject("id"), rs.getString("name"), rs.getString("description"), rs.getString("status"),
                        rs.getInt("courses"), rs.getInt("classes"), rs.getTimestamp("created_at").toInstant(), rs.getLong("version")));
    }

    @Transactional(readOnly = true)
    public TrainingDtos.CurriculumResponse get(AccessScope scope, UUID id) {
        return build(scope, load(scope, id));
    }

    private CRow load(AccessScope scope, UUID id) {
        return jdbc.query("select id, organization_id, name, status, version from curriculum where id = :id and organization_id = :o",
                        new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()),
                        (rs, i) -> new CRow((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3), rs.getString(4), rs.getLong(5)))
                .stream().findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private CRow lock(AccessScope scope, UUID id) {
        load(scope, id);
        return jdbc.query("select id, organization_id, name, status, version from curriculum where id = :id for update", new MapSqlParameterSource("id", id),
                (rs, i) -> new CRow((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3), rs.getString(4), rs.getLong(5))).get(0);
    }

    private TrainingDtos.CurriculumResponse build(AccessScope scope, CRow c) {
        TrainingDtos.CurriculumSummary summary = jdbc.query("select c.id, c.name, c.description, c.status, c.created_at, c.version,"
                        + " (select count(*) from course k where k.curriculum_id = c.id) as courses,"
                        + " (select count(*) from course_class cc join course k on k.id = cc.course_id where k.curriculum_id = c.id) as classes"
                        + " from curriculum c where c.id = :id", new MapSqlParameterSource("id", c.id()),
                        (rs, i) -> new TrainingDtos.CurriculumSummary((UUID) rs.getObject("id"), rs.getString("name"), rs.getString("description"), rs.getString("status"),
                                rs.getInt("courses"), rs.getInt("classes"), rs.getTimestamp("created_at").toInstant(), rs.getLong("version"))).get(0);
        List<TrainingDtos.CourseView> courses = jdbc.query("select k.id, k.order_num, k.name, k.description, k.hours, k.min_attendance_pct, k.pass_grade, k.status, k.version,"
                        + " (select count(*) from course_class cc where cc.course_id = k.id) as classes"
                        + " from course k where k.curriculum_id = :id order by k.order_num", new MapSqlParameterSource("id", c.id()), (rs, i) -> {
            BigDecimal pg = rs.getBigDecimal("pass_grade");
            Integer minPct = (Integer) rs.getObject("min_attendance_pct");
            return new TrainingDtos.CourseView((UUID) rs.getObject("id"), (Integer) rs.getObject("order_num"), rs.getString("name"), rs.getString("description"),
                    rs.getInt("hours"), minPct, pg == null ? null : pg.toPlainString(), rs.getString("status"), null, null, rs.getInt("classes"), rs.getLong("version"));
        });
        return new TrainingDtos.CurriculumResponse(summary, courses);
    }

    // ---------------------------------------------------------------- malla

    @Transactional
    public TrainingDtos.CurriculumResponse create(AuthenticatedActor actor, AccessScope scope, TrainingDtos.CurriculumRequest r) {
        authz.require(actor, MODULE, Action.C);
        requireOrgAdmin(scope);
        String name = TrainingSupport.trim(r == null ? null : r.name(), 120, "nombre");
        if (name == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "nombre");
        }
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(clock.instant());
        try {
            jdbc.update("insert into curriculum (id, organization_id, name, description, status, created_at, created_by, updated_at, updated_by)"
                            + " values (:id, :o, :n, :d, 'DRAFT', :at, :by, :at, :by)",
                    new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("n", name)
                            .addValue("d", TrainingSupport.trim(r.description(), 500, "descripción")).addValue("at", now).addValue("by", actor.ownerId()));
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.training.curriculumNameTaken", HttpStatus.CONFLICT);
        }
        audit.record(new AuditService.Command(MODULE, "CURRICULUM_CREATE", ENTITY, id, scope.organizationId(), null, Map.of("name", name)));
        return get(scope, id);
    }

    @Transactional
    public TrainingDtos.CurriculumResponse update(AuthenticatedActor actor, AccessScope scope, UUID id, TrainingDtos.CurriculumRequest r) {
        authz.require(actor, MODULE, Action.E);
        requireOrgAdmin(scope);
        CRow c = lock(scope, id);
        if ("RETIRED".equals(c.status())) {
            throw new Exceptions("error.training.curriculumRetired", HttpStatus.CONFLICT);
        }
        String name = TrainingSupport.trim(r == null ? null : r.name(), 120, "nombre");
        if (name == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "nombre");
        }
        try {
            int n = jdbc.update("update curriculum set name = :n, description = :d, updated_at = :at, updated_by = :by, version = version + 1"
                            + " where id = :id and (cast(:v as bigint) is null or version = :v)",
                    new MapSqlParameterSource("n", name).addValue("d", TrainingSupport.trim(r.description(), 500, "descripción"))
                            .addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()).addValue("id", id).addValue("v", r.version()));
            if (n == 0) {
                throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
            }
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.training.curriculumNameTaken", HttpStatus.CONFLICT);
        }
        audit.record(new AuditService.Command(MODULE, "CURRICULUM_UPDATE", ENTITY, id, scope.organizationId(), null, Map.of("name", name)));
        return get(scope, id);
    }

    @Transactional
    public void delete(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, MODULE, Action.D);
        requireOrgAdmin(scope);
        CRow c = lock(scope, id);
        Integer n = jdbc.queryForObject("select count(*) from course where curriculum_id = :id", new MapSqlParameterSource("id", id), Integer.class);
        if (n != null && n > 0) {
            throw new Exceptions("error.training.hasDependencies", HttpStatus.UNPROCESSABLE_ENTITY);                     // [V4]
        }
        jdbc.update("delete from curriculum where id = :id", new MapSqlParameterSource("id", id));
        audit.record(new AuditService.Command(MODULE, "CURRICULUM_DELETE", ENTITY, id, scope.organizationId(), null, Map.of("name", c.name())));
    }

    /** [V3] exige ≥1 curso; retira la malla ACTIVE anterior (su historial, dictados y matrículas quedan intactos). */
    @Transactional
    public TrainingDtos.CurriculumResponse activate(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, MODULE, Action.S);
        requireOrgAdmin(scope);
        CRow c = lock(scope, id);
        if ("ACTIVE".equals(c.status())) {
            return get(scope, id);
        }
        if ("RETIRED".equals(c.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, c.status());
        }
        Integer n = jdbc.queryForObject("select count(*) from course where curriculum_id = :id", new MapSqlParameterSource("id", id), Integer.class);
        if (n == null || n == 0) {
            throw new Exceptions("error.training.curriculumEmpty", HttpStatus.UNPROCESSABLE_ENTITY);                     // [V3]
        }
        List<UUID> prev = jdbc.query("select id from curriculum where organization_id = :o and status = 'ACTIVE' for update",
                new MapSqlParameterSource("o", scope.organizationId()), (rs, i) -> (UUID) rs.getObject(1));
        Timestamp now = Timestamp.from(clock.instant());
        if (!prev.isEmpty()) {
            jdbc.update("update curriculum set status = 'RETIRED', updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                    new MapSqlParameterSource("at", now).addValue("by", actor.ownerId()).addValue("id", prev.get(0)));
        }
        jdbc.update("update curriculum set status = 'ACTIVE', updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", now).addValue("by", actor.ownerId()).addValue("id", id));
        audit.record(new AuditService.Command(MODULE, "CURRICULUM_ACTIVATE", ENTITY, id, scope.organizationId(), null,
                Map.of("previous", prev.isEmpty() ? "" : prev.get(0).toString())));
        return get(scope, id);
    }

    // ---------------------------------------------------------------- cursos vinculados

    @Transactional
    public TrainingDtos.CurriculumResponse addCourse(AuthenticatedActor actor, AccessScope scope, UUID curriculumId, TrainingDtos.CourseRequest r) {
        authz.require(actor, MODULE, Action.C);
        requireOrgAdmin(scope);
        CRow c = lock(scope, curriculumId);
        if ("RETIRED".equals(c.status())) {
            throw new Exceptions("error.training.curriculumRetired", HttpStatus.CONFLICT);
        }
        String name = TrainingSupport.trim(r == null ? null : r.name(), 120, "nombre");
        if (name == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "nombre");
        }
        Integer next = jdbc.queryForObject("select coalesce(max(order_num), 0) + 1 from course where curriculum_id = :id", new MapSqlParameterSource("id", curriculumId), Integer.class);
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("insert into course (id, organization_id, curriculum_id, order_num, name, description, hours, min_attendance_pct, pass_grade, status,"
                        + " created_at, created_by, updated_at, updated_by)"
                        + " values (:id, :o, :c, :ord, :n, :d, :h, :map, :pg, 'ACTIVE', :at, :by, :at, :by)",
                new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("c", curriculumId).addValue("ord", next).addValue("n", name)
                        .addValue("d", TrainingSupport.trim(r.description(), 500, "descripción")).addValue("h", r.hours() == null ? 0 : r.hours())
                        .addValue("map", r.minAttendancePct()).addValue("pg", grade(r.passGrade())).addValue("at", now).addValue("by", actor.ownerId()));
        audit.record(new AuditService.Command(MODULE, "COURSE_CREATE", "Course", id, scope.organizationId(), null, Map.of("curriculum", c.name(), "order", next)));
        return get(scope, curriculumId);
    }

    @Transactional
    public TrainingDtos.CurriculumResponse updateCourse(AuthenticatedActor actor, AccessScope scope, UUID curriculumId, UUID courseId, TrainingDtos.CourseRequest r) {
        authz.require(actor, MODULE, Action.E);
        requireOrgAdmin(scope);
        lock(scope, curriculumId);
        assertCourseInCurriculum(curriculumId, courseId);
        String name = TrainingSupport.trim(r == null ? null : r.name(), 120, "nombre");
        if (name == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "nombre");
        }
        int n = jdbc.update("update course set name = :n, description = :d, hours = :h, min_attendance_pct = :map, pass_grade = :pg,"
                        + " updated_at = :at, updated_by = :by, version = version + 1 where id = :id and (cast(:v as bigint) is null or version = :v)",
                new MapSqlParameterSource("n", name).addValue("d", TrainingSupport.trim(r.description(), 500, "descripción")).addValue("h", r.hours() == null ? 0 : r.hours())
                        .addValue("map", r.minAttendancePct()).addValue("pg", grade(r.passGrade())).addValue("at", Timestamp.from(clock.instant()))
                        .addValue("by", actor.ownerId()).addValue("id", courseId).addValue("v", r.version()));
        if (n == 0) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        audit.record(new AuditService.Command(MODULE, "COURSE_UPDATE", "Course", courseId, scope.organizationId(), null, Map.of("name", name)));
        return get(scope, curriculumId);
    }

    /** Solo mientras la malla sigue DRAFT y el curso no tiene dictados [V4]. */
    @Transactional
    public TrainingDtos.CurriculumResponse removeCourse(AuthenticatedActor actor, AccessScope scope, UUID curriculumId, UUID courseId) {
        authz.require(actor, MODULE, Action.D);
        requireOrgAdmin(scope);
        CRow c = lock(scope, curriculumId);
        assertCourseInCurriculum(curriculumId, courseId);
        if (!"DRAFT".equals(c.status())) {
            throw new Exceptions("error.training.curriculumRetired", HttpStatus.CONFLICT);
        }
        Integer n = jdbc.queryForObject("select count(*) from course_class where course_id = :id", new MapSqlParameterSource("id", courseId), Integer.class);
        if (n != null && n > 0) {
            throw new Exceptions("error.training.hasDependencies", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        jdbc.update("delete from course where id = :id", new MapSqlParameterSource("id", courseId));
        jdbc.update("update course set order_num = order_num - 1 where curriculum_id = :c and order_num > (select max(order_num) + 1 from course where curriculum_id = :c)",
                new MapSqlParameterSource("c", curriculumId));
        renumber(curriculumId);
        audit.record(new AuditService.Command(MODULE, "COURSE_DELETE", "Course", courseId, scope.organizationId(), null, Map.of()));
        return get(scope, curriculumId);
    }

    /** [V2] reordena los niveles: exige exactamente los cursos de la malla, sin repetir; solo mientras sigue DRAFT. */
    @Transactional
    public TrainingDtos.CurriculumResponse reorder(AuthenticatedActor actor, AccessScope scope, UUID curriculumId, TrainingDtos.ReorderRequest r) {
        authz.require(actor, MODULE, Action.E);
        requireOrgAdmin(scope);
        CRow c = lock(scope, curriculumId);
        if (!"DRAFT".equals(c.status())) {
            throw new Exceptions("error.training.curriculumRetired", HttpStatus.CONFLICT);
        }
        List<UUID> current = jdbc.query("select id from course where curriculum_id = :id order by order_num", new MapSqlParameterSource("id", curriculumId), (rs, i) -> (UUID) rs.getObject(1));
        List<UUID> requested = r == null || r.courseIds() == null ? List.of() : r.courseIds();
        if (requested.size() != current.size() || !Set.copyOf(requested).equals(Set.copyOf(current))) {
            throw new Exceptions("error.training.orderInvalid", HttpStatus.BAD_REQUEST);                                 // [V2]
        }
        jdbc.update("update course set order_num = order_num + 1000 where curriculum_id = :id", new MapSqlParameterSource("id", curriculumId));
        for (int i = 0; i < requested.size(); i++) {
            jdbc.update("update course set order_num = :o, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                    new MapSqlParameterSource("o", i + 1).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()).addValue("id", requested.get(i)));
        }
        audit.record(new AuditService.Command(MODULE, "COURSE_REORDER", ENTITY, curriculumId, scope.organizationId(), null, Map.of("courses", requested.size())));
        return get(scope, curriculumId);
    }

    private void renumber(UUID curriculumId) {
        List<UUID> ids = jdbc.query("select id from course where curriculum_id = :id order by order_num", new MapSqlParameterSource("id", curriculumId), (rs, i) -> (UUID) rs.getObject(1));
        jdbc.update("update course set order_num = order_num + 1000 where curriculum_id = :id", new MapSqlParameterSource("id", curriculumId));
        for (int i = 0; i < ids.size(); i++) {
            jdbc.update("update course set order_num = :o where id = :id", new MapSqlParameterSource("o", i + 1).addValue("id", ids.get(i)));
        }
    }

    private void assertCourseInCurriculum(UUID curriculumId, UUID courseId) {
        Integer n = jdbc.queryForObject("select count(*) from course where id = :id and curriculum_id = :c",
                new MapSqlParameterSource("id", courseId).addValue("c", curriculumId), Integer.class);
        if (n == null || n == 0) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
    }

    private static void requireOrgAdmin(AccessScope scope) {
        if (scope.role() != RoleType.ORG_ADMIN) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
    }

    static BigDecimal grade(String s) {
        if (!TrainingSupport.hasText(s)) {
            return null;
        }
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "nota");
        }
    }

    // ---------------------------------------------------------------- cursos extra (por sede, sin malla ni prerrequisitos)

    /** Cursos disponibles para abrir un dictado en esa sede: vinculados de la malla ACTIVE + extra propios de la sede. */
    @Transactional(readOnly = true)
    public List<TrainingDtos.CourseView> catalog(AccessScope scope, UUID branchId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("o", scope.organizationId()).addValue("b", branchId);
        return jdbc.query("select k.id, k.order_num, k.name, k.description, k.hours, k.min_attendance_pct, k.pass_grade, k.status, k.version, k.branch_id,"
                        + " cu.name as curriculum_name, b.name as branch_name from course k left join curriculum cu on cu.id = k.curriculum_id"
                        + " left join branch b on b.id = k.branch_id where k.organization_id = :o and k.status = 'ACTIVE'"
                        + " and ((k.curriculum_id in (select id from curriculum where organization_id = :o and status = 'ACTIVE')) or k.branch_id = :b)"
                        + " order by cu.name nulls last, k.order_num, k.name", ps, (rs, i) -> {
            BigDecimal pg = rs.getBigDecimal("pass_grade");
            return new TrainingDtos.CourseView((UUID) rs.getObject("id"), (Integer) rs.getObject("order_num"), rs.getString("name"), rs.getString("description"),
                    rs.getInt("hours"), (Integer) rs.getObject("min_attendance_pct"), pg == null ? null : pg.toPlainString(), rs.getString("status"),
                    (UUID) rs.getObject("branch_id"), rs.getString("branch_name"), 0, rs.getLong("version"));
        });
    }

    record CourseRow(UUID id, UUID orgId, UUID curriculumId, Integer order, UUID branchId, String name, Integer hours, Integer minAttendancePct, BigDecimal passGrade,
                     String status, String curriculumStatus, long version) {
    }

    /** El curso (vinculado o extra) con el estado de su malla, si tiene. Usado por CourseClassService al abrir un dictado [V6]. */
    @Transactional(readOnly = true)
    public CourseRow course(AccessScope scope, UUID courseId) {
        return jdbc.query("select k.id, k.organization_id, k.curriculum_id, k.order_num, k.branch_id, k.name, k.hours, k.min_attendance_pct, k.pass_grade, k.status,"
                        + " cu.status as cur_status, k.version from course k left join curriculum cu on cu.id = k.curriculum_id where k.id = :id and k.organization_id = :o",
                        new MapSqlParameterSource("id", courseId).addValue("o", scope.organizationId()),
                        (rs, i) -> new CourseRow((UUID) rs.getObject(1), (UUID) rs.getObject(2), (UUID) rs.getObject(3), (Integer) rs.getObject(4), (UUID) rs.getObject(5),
                                rs.getString(6), rs.getInt(7), (Integer) rs.getObject(8), rs.getBigDecimal(9), rs.getString(10), rs.getString(11), rs.getLong(12)))
                .stream().findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public TrainingDtos.CourseView createExtra(AuthenticatedActor actor, AccessScope scope, TrainingDtos.CourseRequest r) {
        authz.require(actor, MODULE, Action.C);
        if (r == null || r.branchId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sede");
        }
        if (scope.role() == RoleType.ORG_BRANCH_ADMIN && !scope.canSeeBranch(r.branchId())) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        String name = TrainingSupport.trim(r.name(), 120, "nombre");
        if (name == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "nombre");
        }
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("insert into course (id, organization_id, branch_id, name, description, hours, min_attendance_pct, pass_grade, status, created_at, created_by, updated_at, updated_by)"
                        + " values (:id, :o, :b, :n, :d, :h, :map, :pg, 'ACTIVE', :at, :by, :at, :by)",
                new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("b", r.branchId()).addValue("n", name)
                        .addValue("d", TrainingSupport.trim(r.description(), 500, "descripción")).addValue("h", r.hours() == null ? 0 : r.hours())
                        .addValue("map", r.minAttendancePct()).addValue("pg", grade(r.passGrade())).addValue("at", now).addValue("by", actor.ownerId()));
        audit.record(new AuditService.Command(MODULE, "EXTRA_COURSE_CREATE", "Course", id, scope.organizationId(), r.branchId(), Map.of("name", name)));
        return catalog(scope, r.branchId()).stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public void deleteExtra(AuthenticatedActor actor, AccessScope scope, UUID courseId) {
        authz.require(actor, MODULE, Action.D);
        CourseRow c = course(scope, courseId);
        if (c.curriculumId() != null) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "curso");
        }
        if (scope.role() == RoleType.ORG_BRANCH_ADMIN && !scope.canSeeBranch(c.branchId())) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        Integer n = jdbc.queryForObject("select count(*) from course_class where course_id = :id", new MapSqlParameterSource("id", courseId), Integer.class);
        if (n != null && n > 0) {
            throw new Exceptions("error.training.hasDependencies", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        jdbc.update("delete from course where id = :id", new MapSqlParameterSource("id", courseId));
        audit.record(new AuditService.Command(MODULE, "EXTRA_COURSE_DELETE", "Course", courseId, scope.organizationId(), c.branchId(), Map.of()));
    }
}
