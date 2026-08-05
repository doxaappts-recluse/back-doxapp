package pe.dcs.app.features.bible_academy;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.BibleClass;
import pe.dcs.app.entity.BibleCourse;
import pe.dcs.app.entity.BibleCurriculum;
import pe.dcs.app.entity.BibleEnrollment;
import pe.dcs.app.entity.Ministry;
import pe.dcs.app.entity.MinistryAssignment;
import pe.dcs.app.entity.MinistryRole;
import pe.dcs.app.entity.Organization;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.PersonBranch;
import pe.dcs.app.features.bible_academy.mapper.BibleAcademyMapper;
import pe.dcs.app.features.bible_academy.request.BibleClassFormRequest;
import pe.dcs.app.features.bible_academy.request.BibleClassSearchRequest;
import pe.dcs.app.features.bible_academy.request.BibleCourseFormRequest;
import pe.dcs.app.features.bible_academy.request.BibleCourseSearchRequest;
import pe.dcs.app.features.bible_academy.request.BibleCurriculumFormRequest;
import pe.dcs.app.features.bible_academy.request.BibleCurriculumSearchRequest;
import pe.dcs.app.features.bible_academy.request.BibleEnrollmentFormRequest;
import pe.dcs.app.features.bible_academy.request.BibleEnrollmentSearchRequest;
import pe.dcs.app.features.bible_academy.request.BibleEnrollmentStatusUpdateRequest;
import pe.dcs.app.features.bible_academy.response.BibleClassDetailResponse;
import pe.dcs.app.features.bible_academy.response.BibleClassSearchRowResponse;
import pe.dcs.app.features.bible_academy.response.BibleCourseResponse;
import pe.dcs.app.features.bible_academy.response.BibleCurriculumDetailResponse;
import pe.dcs.app.features.bible_academy.response.BibleCurriculumSearchRowResponse;
import pe.dcs.app.features.bible_academy.response.BibleEnrollmentResponse;
import pe.dcs.app.features.bible_academy.response.BiblePersonSearchResponse;
import pe.dcs.app.features.bible_academy.service.BibleAcademyService;
import pe.dcs.app.repository.BibleClassRepository;
import pe.dcs.app.repository.BibleCourseRepository;
import pe.dcs.app.repository.BibleCurriculumRepository;
import pe.dcs.app.repository.BibleEnrollmentRepository;
import pe.dcs.app.repository.BranchRepository;
import pe.dcs.app.repository.MembershipRepository;
import pe.dcs.app.repository.MinistryAssignmentRepository;
import pe.dcs.app.repository.MinistryRepository;
import pe.dcs.app.repository.MinistryRoleRepository;
import pe.dcs.app.repository.OrganizationRepository;
import pe.dcs.app.repository.PersonRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.bible_academy.BibleClassStatus;
import pe.dcs.app.util.enums.bible_academy.BibleCurriculumStatus;
import pe.dcs.app.util.enums.bible_academy.BibleEnrollmentStatus;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Academia Bíblica. Dos jerarquías bien distintas conviven acá (ver
 * memoria del usuario / diseño acordado):
 *
 * - Malla curricular (BibleCurriculum → BibleCourse con order):
 *   compartida por toda la organización, exclusiva de org admin,
 *   solo UNA ACTIVE a la vez (activar una nueva retira la anterior
 *   sola, igual criterio que Contract.markReplaced pero sin fechas).
 * - Cursos extra + dictados (BibleClass) + matrículas
 *   (BibleEnrollment): por sede, delegable a branch admin/org user.
 *
 * El prerequisito de un nivel de malla (aprobar el nivel N-1 en
 * CUALQUIER sede antes de matricularse al N) se valida acá mismo
 * (ver hasApprovedPrerequisite) — puede saltarse manualmente con un
 * override admin + motivo obligatorio en vez de una pantalla de
 * migración de malla dedicada.
 */
@Service
@RequiredArgsConstructor
public class BibleAcademyServiceImpl implements BibleAcademyService {

    private static final String TEACHER_MINISTRY_CODE = "ACADEMIA_BIBLICA";
    private static final String TEACHER_MINISTRY_NAME_ES = "Academia Bíblica";
    private static final String TEACHER_MINISTRY_NAME_EN = "Bible Academy";
    private static final String TEACHER_ROLE_CODE = "MAESTRO_ACADEMIA_BIBLICA";
    private static final String TEACHER_ROLE_NAME_ES = "Maestro de Academia Bíblica";
    private static final String TEACHER_ROLE_NAME_EN = "Bible Academy Teacher";

    private final BibleCurriculumRepository bibleCurriculumRepository;
    private final BibleCourseRepository bibleCourseRepository;
    private final BibleClassRepository bibleClassRepository;
    private final BibleEnrollmentRepository bibleEnrollmentRepository;
    private final BranchRepository branchRepository;
    private final PersonRepository personRepository;
    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final MinistryRepository ministryRepository;
    private final MinistryRoleRepository ministryRoleRepository;
    private final MinistryAssignmentRepository ministryAssignmentRepository;
    private final BibleAcademyMapper mapper;
    private final AuthContext authContext;
    private final BibleAcademyAccessGuard accessGuard;

    // =====================================================
    // BUSCAR PERSONA POR DNI (maestro o alumno)
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public BiblePersonSearchResponse findPersonByDni(String dni) {

        accessGuard.assertCanUse();

        UUID organizationId = authContext.getCurrentOrganizationId();

        if (organizationId == null) {
            throw new Exceptions("error.noTieneContextoOrganizacionActivo", HttpStatus.FORBIDDEN);
        }

        if (dni == null || dni.isBlank()) {
            throw new Exceptions("error.elDniEsObligatorio", HttpStatus.BAD_REQUEST);
        }

        Person person =
                personRepository.findByDniInOrganization(dni, organizationId)
                        .orElseThrow(() -> new Exceptions(
                                "error.noEncontroNingunaPersonaDniOrganizacion",
                                HttpStatus.NOT_FOUND
                        ));

        boolean isMember =
                membershipRepository.existsByPersonIdAndCurrentTrueAndStatus(person.getId(), StatusType.ACTIVE);

        return new BiblePersonSearchResponse(
                person.getId(),
                person.getName(),
                person.getLastname(),
                person.getDni(),
                isMember
        );
    }

    // =====================================================
    // MALLA CURRICULAR
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BibleCurriculumSearchRowResponse> searchCurriculums(BibleCurriculumSearchRequest request) {

        accessGuard.assertCanUse();

        Pageable pageable = PageableUtil.buildPageable(request.getPagination(), request.getSorts());

        Page<BibleCurriculum> page =
                bibleCurriculumRepository.findAll(
                        BibleCurriculumSpecification.filter(request.getFilters(), authContext),
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();
        boolean canManage = authContext.isCurrentOrganizationAdmin();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(c -> mapper.toCurriculumSearchRow(
                                c,
                                bibleCourseRepository.findByCurriculumIdOrderByOrderAsc(c.getId()).size(),
                                canManage,
                                showAudit
                        ))
                        .toList(),
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public BibleCurriculumDetailResponse getCurriculumById(UUID id) {

        BibleCurriculum curriculum = findCurriculumOrThrow(id);

        accessGuard.assertSameOrganizationCurriculum(curriculum);
        accessGuard.assertCanUse();

        boolean showAudit = authContext.canViewAudit();
        boolean canManage = authContext.isCurrentOrganizationAdmin();

        List<BibleCourseResponse> courses =
                bibleCourseRepository.findByCurriculumIdOrderByOrderAsc(id)
                        .stream()
                        .map(course -> mapper.toCourseResponse(
                                course,
                                bibleClassRepository.countByCourseId(course.getId()),
                                accessGuard.canManageCourse(course),
                                showAudit
                        ))
                        .toList();

        return mapper.toCurriculumDetailResponse(curriculum, courses, canManage);
    }

    @Override
    @Transactional
    public UUID createCurriculum(BibleCurriculumFormRequest request) {

        accessGuard.assertCanManageCurriculum();

        validateCurriculumForm(request);

        UUID organizationId = authContext.getCurrentOrganizationId();

        if (organizationId == null) {
            throw new Exceptions("error.noTieneContextoOrganizacionActivo", HttpStatus.FORBIDDEN);
        }

        Organization organization =
                organizationRepository.findById(organizationId)
                        .orElseThrow(() -> new Exceptions("error.organizacionNoEncontrada2", HttpStatus.NOT_FOUND));

        BibleCurriculum curriculum = new BibleCurriculum();
        curriculum.setOrganization(organization);
        curriculum.setStatus(BibleCurriculumStatus.DRAFT);
        curriculum.setName(request.getName());
        curriculum.setDescription(request.getDescription());

        bibleCurriculumRepository.save(curriculum);

        return curriculum.getId();
    }

    @Override
    @Transactional
    public void updateCurriculum(UUID id, BibleCurriculumFormRequest request) {

        BibleCurriculum curriculum = findCurriculumOrThrow(id);

        accessGuard.assertSameOrganizationCurriculum(curriculum);
        accessGuard.assertCanManageCurriculum();

        validateCurriculumForm(request);

        curriculum.setName(request.getName());
        curriculum.setDescription(request.getDescription());

        bibleCurriculumRepository.save(curriculum);
    }

    /**
     * Solo puede haber UNA malla ACTIVE por organización — activar
     * esta retira automáticamente cualquier otra que ya estuviera
     * ACTIVE de la misma organización (mismo criterio que
     * Contract.markReplaced, sin fechas/scheduler porque acá no hay
     * vigencia temporal).
     */
    @Override
    @Transactional
    public void activateCurriculum(UUID id) {

        BibleCurriculum curriculum = findCurriculumOrThrow(id);

        accessGuard.assertSameOrganizationCurriculum(curriculum);
        accessGuard.assertCanManageCurriculum();

        if (curriculum.getStatus() == BibleCurriculumStatus.ACTIVE) {
            return;
        }

        bibleCurriculumRepository.findByOrganizationIdAndStatus(
                        curriculum.getOrganization().getId(),
                        BibleCurriculumStatus.ACTIVE
                )
                .filter(previous -> !previous.getId().equals(curriculum.getId()))
                .ifPresent(previous -> {
                    previous.setStatus(BibleCurriculumStatus.RETIRED);
                    bibleCurriculumRepository.save(previous);
                });

        curriculum.setStatus(BibleCurriculumStatus.ACTIVE);
        bibleCurriculumRepository.save(curriculum);
    }

    @Override
    @Transactional
    public void retireCurriculum(UUID id) {

        BibleCurriculum curriculum = findCurriculumOrThrow(id);

        accessGuard.assertSameOrganizationCurriculum(curriculum);
        accessGuard.assertCanManageCurriculum();

        curriculum.setStatus(BibleCurriculumStatus.RETIRED);
        bibleCurriculumRepository.save(curriculum);
    }

    // =====================================================
    // CURSOS (malla y extra)
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BibleCourseResponse> searchCourses(BibleCourseSearchRequest request) {

        accessGuard.assertCanUse();

        Pageable pageable = PageableUtil.buildPageable(request.getPagination(), request.getSorts());

        Page<BibleCourse> page =
                bibleCourseRepository.findAll(
                        BibleCourseSpecification.filter(request.getFilters(), authContext),
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(course -> mapper.toCourseResponse(
                                course,
                                bibleClassRepository.countByCourseId(course.getId()),
                                accessGuard.canManageCourse(course),
                                showAudit
                        ))
                        .toList(),
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public BibleCourseResponse getCourseById(UUID id) {

        accessGuard.assertCanUse();

        BibleCourse course = findCourseOrThrow(id);

        return mapper.toCourseResponse(
                course,
                bibleClassRepository.countByCourseId(course.getId()),
                accessGuard.canManageCourse(course),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional
    public UUID createCourse(BibleCourseFormRequest request) {

        validateCourseForm(request);

        BibleCourse course = new BibleCourse();
        course.setName(request.getName());
        course.setDescription(request.getDescription());
        course.setStatus(request.getStatus() != null ? request.getStatus() : StatusType.ACTIVE);

        if (request.getCurriculumId() != null) {

            accessGuard.assertCanManageCurriculum();

            BibleCurriculum curriculum = findCurriculumOrThrow(request.getCurriculumId());
            accessGuard.assertSameOrganizationCurriculum(curriculum);

            if (curriculum.getStatus() == BibleCurriculumStatus.RETIRED) {
                throw new Exceptions(
                        "error.noPuedenAgregarNivelesMallaRetirada",
                        HttpStatus.BAD_REQUEST
                );
            }

            if (bibleCourseRepository.existsByCurriculumIdAndOrder(curriculum.getId(), request.getOrder())) {
                throw new Exceptions(
                        "error.yaExisteNivelEnMalla",
                        HttpStatus.BAD_REQUEST,
                        request.getOrder()
                );
            }

            course.setCurriculum(curriculum);
            course.setOrder(request.getOrder());

        } else {

            accessGuard.assertCanCreateExtraCourse();

            UUID branchId = accessGuard.resolveBranchId(request.getBranchId());

            if (branchId == null) {
                throw new Exceptions("error.debeSeleccionarSedeCurso", HttpStatus.BAD_REQUEST);
            }

            course.setBranch(findBranchOrThrow(branchId));
        }

        bibleCourseRepository.save(course);

        return course.getId();
    }

    @Override
    @Transactional
    public void updateCourse(UUID id, BibleCourseFormRequest request) {

        BibleCourse course = findCourseOrThrow(id);

        accessGuard.assertCanManageCourse(course);

        course.setName(request.getName());
        course.setDescription(request.getDescription());

        if (request.getStatus() != null) {
            course.setStatus(request.getStatus());
        }

        if (!course.isExtra() && request.getOrder() != null && !request.getOrder().equals(course.getOrder())) {

            boolean duplicated =
                    bibleCourseRepository.findByCurriculumIdAndOrder(course.getCurriculum().getId(), request.getOrder())
                            .filter(other -> !other.getId().equals(course.getId()))
                            .isPresent();

            if (duplicated) {
                throw new Exceptions(
                        "error.yaExisteNivelEnMalla",
                        HttpStatus.BAD_REQUEST,
                        request.getOrder()
                );
            }

            course.setOrder(request.getOrder());
        }

        bibleCourseRepository.save(course);
    }

    // =====================================================
    // DICTADOS (BibleClass)
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BibleClassSearchRowResponse> searchClasses(BibleClassSearchRequest request) {

        accessGuard.assertCanUse();

        Pageable pageable = PageableUtil.buildPageable(request.getPagination(), request.getSorts());

        Page<BibleClass> page =
                bibleClassRepository.findAll(
                        BibleClassSpecification.filter(request.getFilters(), authContext),
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(c -> mapper.toClassSearchRow(
                                c,
                                bibleEnrollmentRepository.countByBibleClassId(c.getId()),
                                accessGuard.canManageClass(c),
                                showAudit
                        ))
                        .toList(),
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public BibleClassDetailResponse getClassById(UUID id) {

        BibleClass bibleClass = findClassOrThrow(id);

        return mapper.toClassDetailResponse(
                bibleClass,
                bibleEnrollmentRepository.countByBibleClassId(id),
                accessGuard.canManageClass(bibleClass)
        );
    }

    @Override
    @Transactional
    public UUID createClass(BibleClassFormRequest request) {

        accessGuard.assertCanCreateClass();

        validateClassForm(request);

        BibleCourse course = findCourseOrThrow(request.getCourseId());

        assertCourseDictable(course);

        UUID branchId = accessGuard.resolveBranchId(request.getBranchId());

        if (branchId == null) {
            throw new Exceptions("error.debeSeleccionarSedeDictado", HttpStatus.BAD_REQUEST);
        }

        BibleClass bibleClass = new BibleClass();
        bibleClass.setCourse(course);
        bibleClass.setStatus(request.getStatus() != null ? request.getStatus() : BibleClassStatus.PLANNED);

        applyClassForm(bibleClass, request, findBranchOrThrow(branchId));

        bibleClassRepository.save(bibleClass);

        syncTeacherMinistryService(bibleClass);

        bibleClassRepository.save(bibleClass);

        return bibleClass.getId();
    }

    @Override
    @Transactional
    public void updateClass(UUID id, BibleClassFormRequest request) {

        BibleClass bibleClass = findClassOrThrow(id);

        accessGuard.assertCanManageClass(bibleClass);

        validateClassForm(request);

        Branch branch =
                request.getBranchId() != null
                        ? findBranchOrThrow(accessGuard.resolveBranchId(request.getBranchId()))
                        : bibleClass.getBranch();

        applyClassForm(bibleClass, request, branch);

        syncTeacherMinistryService(bibleClass);

        bibleClassRepository.save(bibleClass);
    }

    // =====================================================
    // MATRÍCULAS (BibleEnrollment)
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BibleEnrollmentResponse> searchEnrollments(UUID classId, BibleEnrollmentSearchRequest request) {

        BibleClass bibleClass = findClassOrThrow(classId);

        accessGuard.assertCanManageClass(bibleClass);

        Pageable pageable = PageableUtil.buildPageable(request.getPagination(), request.getSorts());

        Page<BibleEnrollment> page =
                bibleEnrollmentRepository.findAll(
                        BibleEnrollmentSpecification.filter(classId, request.getFilters()),
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();
        boolean canManage = accessGuard.canManageClass(bibleClass);

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(e -> mapper.toEnrollmentResponse(e, canManage, showAudit))
                        .toList(),
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    @Override
    @Transactional
    public UUID createEnrollment(UUID classId, BibleEnrollmentFormRequest request) {

        BibleClass bibleClass = findClassOrThrow(classId);

        accessGuard.assertCanManageClass(bibleClass);

        if (request.getPersonId() == null) {
            throw new Exceptions("error.debeSeleccionarPersonaMatricular", HttpStatus.BAD_REQUEST);
        }

        Person person = findPersonOrThrow(request.getPersonId());

        if (bibleEnrollmentRepository.existsByBibleClassIdAndPersonId(classId, person.getId())) {
            throw new Exceptions("error.personaMatriculadaDictado", HttpStatus.BAD_REQUEST);
        }

        if (bibleClass.getCapacity() != null
                && bibleEnrollmentRepository.countByBibleClassId(classId) >= bibleClass.getCapacity()) {
            throw new Exceptions("error.dictadoAlcanzoCupoMaximo", HttpStatus.BAD_REQUEST);
        }

        BibleEnrollment enrollment = new BibleEnrollment();
        enrollment.setBibleClass(bibleClass);
        enrollment.setPerson(person);
        enrollment.setEnrollDate(request.getEnrollDate() != null ? request.getEnrollDate() : LocalDate.now());
        enrollment.setStatus(BibleEnrollmentStatus.ENROLLED);

        assertPrerequisiteSatisfied(bibleClass, person, request, enrollment);

        bibleEnrollmentRepository.save(enrollment);

        return enrollment.getId();
    }

    @Override
    @Transactional
    public void updateEnrollmentStatus(UUID classId, UUID enrollmentId, BibleEnrollmentStatusUpdateRequest request) {

        BibleClass bibleClass = findClassOrThrow(classId);

        accessGuard.assertCanManageClass(bibleClass);

        BibleEnrollment enrollment =
                bibleEnrollmentRepository.findById(enrollmentId)
                        .orElseThrow(() -> new Exceptions("error.matriculaNoEncontrada", HttpStatus.NOT_FOUND));

        if (!enrollment.getBibleClass().getId().equals(classId)) {
            throw new Exceptions("error.matriculaNoPerteneceDictado", HttpStatus.BAD_REQUEST);
        }

        if (request.getStatus() == null) {
            throw new Exceptions("error.elEstadoEsObligatorio", HttpStatus.BAD_REQUEST);
        }

        boolean requiresReason =
                request.getStatus() == BibleEnrollmentStatus.FAILED
                        || request.getStatus() == BibleEnrollmentStatus.WITHDRAWN;

        if (requiresReason && (request.getStatusReason() == null || request.getStatusReason().isBlank())) {
            throw new Exceptions(
                    "error.debeIndicarMotivoCuandoEstadoReprobado",
                    HttpStatus.BAD_REQUEST
            );
        }

        enrollment.setStatus(request.getStatus());
        enrollment.setFinalGrade(request.getFinalGrade());
        enrollment.setStatusReason(requiresReason ? request.getStatusReason() : null);

        bibleEnrollmentRepository.save(enrollment);
    }

    // =====================================================
    // HELPERS — VALIDACIÓN
    // =====================================================

    private void validateCurriculumForm(BibleCurriculumFormRequest request) {

        if (request.getName() == null || request.getName().isBlank()) {
            throw new Exceptions("error.nombreMallaObligatorio", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateCourseForm(BibleCourseFormRequest request) {

        if (request.getName() == null || request.getName().isBlank()) {
            throw new Exceptions("error.nombreCursoObligatorio", HttpStatus.BAD_REQUEST);
        }

        boolean isCurriculumCourse = request.getCurriculumId() != null;
        boolean isExtraCourse = request.getBranchId() != null;

        if (isCurriculumCourse == isExtraCourse) {
            throw new Exceptions(
                    "error.cursoDebePertenecerMallaNivelSer",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (isCurriculumCourse && (request.getOrder() == null || request.getOrder() < 1)) {
            throw new Exceptions("error.nivelOrderObligatorioDebeSerMayor", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateClassForm(BibleClassFormRequest request) {

        if (request.getCourseId() == null) {
            throw new Exceptions("error.debeSeleccionarCursoDictado", HttpStatus.BAD_REQUEST);
        }

        if (request.getTeacherPersonId() == null
                && (request.getTeacherName() == null || request.getTeacherName().isBlank())) {

            throw new Exceptions("error.maestroDictadoObligatorio", HttpStatus.BAD_REQUEST);
        }

        if (request.getStartDate() == null) {
            throw new Exceptions("error.fechaInicioDictadoObligatoria", HttpStatus.BAD_REQUEST);
        }

        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new Exceptions(
                    "error.fechaFinNoPuedeSerAnterior",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /** "La sede solo puede usar la malla activa" — pedido explícito del usuario. */
    private void assertCourseDictable(BibleCourse course) {

        if (course.isExtra()) {
            return;
        }

        if (course.getCurriculum().getStatus() != BibleCurriculumStatus.ACTIVE) {
            throw new Exceptions(
                    "error.soloPuedenAbrirDictadosCursosPertenezcan",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * Prerequisito: para matricularse en el nivel N de una malla, la
     * persona debe tener APPROVED el nivel N-1 de esa MISMA malla,
     * en CUALQUIER sede (la malla es de toda la organización). Sin
     * prerequisito para el nivel 1 ni para cursos extra. Un admin
     * puede saltarse esto dejando un motivo obligatorio (ver
     * BibleAcademyAccessGuard.assertCanOverridePrerequisite).
     */
    private void assertPrerequisiteSatisfied(
            BibleClass bibleClass,
            Person person,
            BibleEnrollmentFormRequest request,
            BibleEnrollment enrollment
    ) {

        BibleCourse course = bibleClass.getCourse();

        if (course.isExtra() || course.getOrder() == null || course.getOrder() <= 1) {
            return;
        }

        BibleCourse prerequisiteCourse =
                bibleCourseRepository.findByCurriculumIdAndOrder(
                        course.getCurriculum().getId(),
                        course.getOrder() - 1
                ).orElse(null);

        if (prerequisiteCourse == null) {
            return;
        }

        boolean approved =
                bibleEnrollmentRepository.existsByPersonIdAndBibleClass_Course_IdAndStatus(
                        person.getId(),
                        prerequisiteCourse.getId(),
                        BibleEnrollmentStatus.APPROVED
                );

        if (approved) {
            return;
        }

        if (!request.isOverridePrerequisite()) {
            throw new Exceptions(
                    "error.debeAprobarPrimeroAntesMatricularse",
                    HttpStatus.BAD_REQUEST,
                    prerequisiteCourse.getName()
            );
        }

        accessGuard.assertCanOverridePrerequisite(bibleClass);

        if (request.getOverrideReason() == null || request.getOverrideReason().isBlank()) {
            throw new Exceptions(
                    "error.debeIndicarMotivoSaltarPrerequisito",
                    HttpStatus.BAD_REQUEST
            );
        }

        enrollment.setPrerequisiteOverridden(true);
        enrollment.setOverrideReason(request.getOverrideReason());
    }

    // =====================================================
    // HELPERS — GENÉRICOS
    // =====================================================

    private void applyClassForm(BibleClass bibleClass, BibleClassFormRequest request, Branch branch) {

        bibleClass.setBranch(branch);
        bibleClass.setTeacherName(request.getTeacherName());
        bibleClass.setTeacherPerson(
                request.getTeacherPersonId() != null
                        ? findPersonOrThrow(request.getTeacherPersonId())
                        : null
        );
        bibleClass.setMeetingDay(request.getMeetingDay());
        bibleClass.setMeetingTime(request.getMeetingTime());
        bibleClass.setLocation(request.getLocation());
        bibleClass.setStartDate(request.getStartDate());
        bibleClass.setEndDate(request.getEndDate());
        bibleClass.setCapacity(request.getCapacity());

        if (request.getStatus() != null) {
            bibleClass.setStatus(request.getStatus());
        }
    }

    /**
     * Refleja al maestro actual del dictado como servicio ministerial
     * mientras dure — igual patrón que
     * SmallGroupServiceImpl.syncLeaderMinistryService.
     */
    private void syncTeacherMinistryService(BibleClass bibleClass) {

        Person teacher = bibleClass.getTeacherPerson();

        if (teacher == null) {

            MinistryAssignment existing = bibleClass.getMinistryAssignment();

            if (existing != null && existing.getEndDate() == null) {
                existing.setEndDate(LocalDate.now());
                ministryAssignmentRepository.save(existing);
            }

            bibleClass.setMinistryAssignment(null);
            return;
        }

        PersonBranch activeBranch = findActiveBranch(teacher);

        if (activeBranch == null) {
            return;
        }

        Ministry ministry = findOrCreateTeacherMinistry();
        MinistryRole role = findOrCreateTeacherRole(ministry);

        MinistryAssignment assignment =
                bibleClass.getMinistryAssignment() != null
                        ? bibleClass.getMinistryAssignment()
                        : new MinistryAssignment();

        assignment.setPerson(teacher);
        assignment.setMinistry(ministry);
        assignment.setMinistryRole(role);
        assignment.setStartDate(bibleClass.getStartDate());
        assignment.setEndDate(bibleClass.getEndDate());
        assignment.setReason("Dictado de Academia Bíblica: " + bibleClass.getCourse().getName());
        assignment.setBranch(activeBranch.getBranch());

        ministryAssignmentRepository.save(assignment);

        bibleClass.setMinistryAssignment(assignment);
    }

    private PersonBranch findActiveBranch(Person person) {

        return person.getBranchHistory()
                .stream()
                .filter(pb -> pb.getStatus() == StatusType.ACTIVE)
                .findFirst()
                .orElse(null);
    }

    private Ministry findOrCreateTeacherMinistry() {

        return ministryRepository.findByCode(TEACHER_MINISTRY_CODE)
                .orElseGet(() -> {

                    Ministry ministry = new Ministry();
                    ministry.setCode(TEACHER_MINISTRY_CODE);
                    ministry.setNameEs(TEACHER_MINISTRY_NAME_ES);
                    ministry.setNameEn(TEACHER_MINISTRY_NAME_EN);
                    ministry.setDescription(
                            "Generado automáticamente para registrar el servicio de los maestros de la Academia Bíblica."
                    );
                    ministry.setStatus(StatusType.ACTIVE);
                    ministry.setRequiresActiveMembership(false);

                    return ministryRepository.save(ministry);
                });
    }

    private MinistryRole findOrCreateTeacherRole(Ministry ministry) {

        return ministryRoleRepository.findByMinistryIdAndCode(ministry.getId(), TEACHER_ROLE_CODE)
                .orElseGet(() -> {

                    MinistryRole role = new MinistryRole();
                    role.setCode(TEACHER_ROLE_CODE);
                    role.setNameEs(TEACHER_ROLE_NAME_ES);
                    role.setNameEn(TEACHER_ROLE_NAME_EN);
                    role.setMinistry(ministry);
                    role.setStatus(StatusType.ACTIVE);
                    role.setRequiresActiveMembership(false);

                    return ministryRoleRepository.save(role);
                });
    }

    private BibleCurriculum findCurriculumOrThrow(UUID id) {
        return bibleCurriculumRepository.findById(id)
                .orElseThrow(() -> new Exceptions("error.mallaCurricularNoEncontrada", HttpStatus.NOT_FOUND));
    }

    private BibleCourse findCourseOrThrow(UUID id) {
        return bibleCourseRepository.findById(id)
                .orElseThrow(() -> new Exceptions("error.cursoNoEncontrado", HttpStatus.NOT_FOUND));
    }

    private BibleClass findClassOrThrow(UUID id) {
        return bibleClassRepository.findById(id)
                .orElseThrow(() -> new Exceptions("error.dictadoNoEncontrado", HttpStatus.NOT_FOUND));
    }

    private Branch findBranchOrThrow(UUID id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new Exceptions("error.sedeNoEncontrada2", HttpStatus.NOT_FOUND));
    }

    private Person findPersonOrThrow(UUID id) {
        return personRepository.findById(id)
                .orElseThrow(() -> new Exceptions("error.personaNoEncontrada", HttpStatus.NOT_FOUND));
    }
}
