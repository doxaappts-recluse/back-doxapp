package pe.dcs.app.features.church_attendance;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.ChurchService;
import pe.dcs.app.entity.ChurchServiceAttendance;
import pe.dcs.app.entity.Person;
import pe.dcs.app.features.church_attendance.mapper.ChurchServiceMapper;
import pe.dcs.app.features.church_attendance.request.ChurchServiceAttendanceFormRequest;
import pe.dcs.app.features.church_attendance.request.ChurchServiceFormRequest;
import pe.dcs.app.features.church_attendance.request.ChurchServiceSearchRequest;
import pe.dcs.app.features.church_attendance.response.ChurchPersonSearchResponse;
import pe.dcs.app.features.church_attendance.response.ChurchServiceAttendanceResponse;
import pe.dcs.app.features.church_attendance.response.ChurchServiceDetailResponse;
import pe.dcs.app.features.church_attendance.response.ChurchServiceSearchRowResponse;
import pe.dcs.app.features.church_attendance.service.ChurchServiceService;
import pe.dcs.app.features.pastoral_followup.PastoralFollowUpAccessGuard;
import pe.dcs.app.repository.BranchRepository;
import pe.dcs.app.repository.ChurchServiceAttendanceRepository;
import pe.dcs.app.repository.ChurchServiceRepository;
import pe.dcs.app.repository.MembershipRepository;
import pe.dcs.app.repository.PersonRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Asistencia a Cultos: catálogo de cultos recurrentes por sede
 * (ChurchService) + registro de quién asistió en cada fecha
 * (ChurchServiceAttendance). Reutiliza el módulo/permisos de
 * Seguimiento Pastoral (PASTORAL_FOLLOWUP) — forma parte del mismo
 * paquete comercial "CRM Pastoral", sin módulo ni línea de precio
 * propios (ver PastoralFollowUpAccessGuard, import.sql).
 *
 * A diferencia de Grupos Pequeños, acá SIEMPRE se marca presente a
 * una Person que ya existe (buscada por DNI) — no hay invitados de
 * solo nombre en esta primera versión.
 */
@Service
@RequiredArgsConstructor
public class ChurchServiceServiceImpl implements ChurchServiceService {

    private final ChurchServiceRepository churchServiceRepository;
    private final ChurchServiceAttendanceRepository attendanceRepository;
    private final PersonRepository personRepository;
    private final BranchRepository branchRepository;
    private final MembershipRepository membershipRepository;
    private final ChurchServiceMapper mapper;
    private final AuthContext authContext;
    private final PastoralFollowUpAccessGuard accessGuard;

    // =====================================================
    // CATALOGO DE CULTOS
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChurchServiceSearchRowResponse> search(ChurchServiceSearchRequest request) {

        accessGuard.assertCanUse();

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        Page<ChurchService> page =
                churchServiceRepository.findAll(
                        ChurchServiceSpecification.filter(request.getFilters(), authContext),
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(cs -> mapper.toSearchRow(
                                cs,
                                accessGuard.canManage(cs.getBranch()),
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
    public ChurchServiceDetailResponse getById(UUID id) {

        ChurchService churchService = findOrThrow(id);

        assertSameOrganization(churchService.getBranch());

        return mapper.toDetailResponse(
                churchService,
                accessGuard.canManage(churchService.getBranch())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ChurchPersonSearchResponse findPersonByDni(String dni) {

        accessGuard.assertCanUse();

        UUID organizationId = authContext.getCurrentOrganizationId();

        if (organizationId == null) {
            throw new Exceptions(
                    "No tiene un contexto de organización activo.",
                    HttpStatus.FORBIDDEN
            );
        }

        if (dni == null || dni.isBlank()) {
            throw new Exceptions(
                    "El DNI es obligatorio.",
                    HttpStatus.BAD_REQUEST
            );
        }

        Person person =
                personRepository.findByDniInOrganization(dni, organizationId)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "No se encontró ninguna persona con ese DNI en la organización.",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        return new ChurchPersonSearchResponse(
                person.getId(),
                person.getName(),
                person.getLastname(),
                person.getDni(),
                isActiveMember(person)
        );
    }

    @Override
    @Transactional
    public UUID create(ChurchServiceFormRequest request) {

        accessGuard.assertCanCreate();

        validateForm(request);

        Branch branch = resolveBranch(request.getBranchId());

        ChurchService churchService = new ChurchService();
        churchService.setStatus(StatusType.ACTIVE);

        applyForm(churchService, request, branch);

        churchServiceRepository.save(churchService);

        return churchService.getId();
    }

    @Override
    @Transactional
    public void update(UUID id, ChurchServiceFormRequest request) {

        ChurchService churchService = findOrThrow(id);

        assertSameOrganization(churchService.getBranch());
        accessGuard.assertCanManage(churchService.getBranch());

        validateForm(request);

        Branch branch =
                request.getBranchId() != null
                        ? resolveBranch(request.getBranchId())
                        : churchService.getBranch();

        applyForm(churchService, request, branch);

        churchServiceRepository.save(churchService);
    }

    // =====================================================
    // ASISTENCIA
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public List<ChurchServiceAttendanceResponse> listAttendance(UUID churchServiceId, LocalDate date) {

        ChurchService churchService = findOrThrow(churchServiceId);

        assertSameOrganization(churchService.getBranch());

        LocalDate safeDate = date != null ? date : LocalDate.now();

        return attendanceRepository.findByChurchServiceIdAndAttendanceDateOrderByPerson_LastnameAsc(
                        churchServiceId,
                        safeDate
                )
                .stream()
                .map(a -> mapper.toAttendanceResponse(a, isActiveMember(a.getPerson())))
                .toList();
    }

    @Override
    @Transactional
    public void markAttendance(UUID churchServiceId, ChurchServiceAttendanceFormRequest request) {

        ChurchService churchService = findOrThrow(churchServiceId);

        assertSameOrganization(churchService.getBranch());
        accessGuard.assertCanManage(churchService.getBranch());

        if (request.getPersonId() == null) {
            throw new Exceptions(
                    "Debe seleccionar la persona que asistió.",
                    HttpStatus.BAD_REQUEST
            );
        }

        LocalDate attendanceDate =
                request.getAttendanceDate() != null
                        ? request.getAttendanceDate()
                        : LocalDate.now();

        Person person = findPersonOrThrow(request.getPersonId());

        if (attendanceRepository.existsByChurchServiceIdAndPersonIdAndAttendanceDate(
                churchServiceId, person.getId(), attendanceDate)) {

            throw new Exceptions(
                    "Esta persona ya fue marcada como presente en este culto y fecha.",
                    HttpStatus.BAD_REQUEST
            );
        }

        ChurchServiceAttendance attendance = new ChurchServiceAttendance();
        attendance.setChurchService(churchService);
        attendance.setPerson(person);
        attendance.setAttendanceDate(attendanceDate);
        attendance.setObservations(request.getObservations());

        attendanceRepository.save(attendance);
    }

    @Override
    @Transactional
    public void removeAttendance(UUID churchServiceId, UUID attendanceId) {

        ChurchService churchService = findOrThrow(churchServiceId);

        assertSameOrganization(churchService.getBranch());
        accessGuard.assertCanManage(churchService.getBranch());

        ChurchServiceAttendance attendance =
                attendanceRepository.findById(attendanceId)
                        .orElseThrow(() ->
                                new Exceptions("Registro de asistencia no encontrado.", HttpStatus.NOT_FOUND)
                        );

        if (!attendance.getChurchService().getId().equals(churchServiceId)) {
            throw new Exceptions(
                    "Este registro de asistencia no pertenece a este culto.",
                    HttpStatus.BAD_REQUEST
            );
        }

        attendanceRepository.delete(attendance);
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private void applyForm(ChurchService churchService, ChurchServiceFormRequest request, Branch branch) {

        churchService.setName(request.getName());
        churchService.setDayOfWeek(request.getDayOfWeek());
        churchService.setTimeOfDay(request.getTimeOfDay());

        if (request.getStatus() != null) {
            churchService.setStatus(request.getStatus());
        }

        churchService.setBranch(branch);
    }

    private boolean isActiveMember(Person person) {

        return person != null
                && membershipRepository.existsByPersonIdAndCurrentTrueAndStatus(
                        person.getId(),
                        StatusType.ACTIVE
                );
    }

    /**
     * Igual criterio que MarriageServiceImpl.resolveBranch: solo el
     * org admin elige libremente la sede; cualquier otro rol queda
     * ligado a su propia sede actual.
     */
    private Branch resolveBranch(UUID branchId) {

        UUID resolvedId = accessGuard.resolveBranchId(branchId);

        if (resolvedId == null) {
            throw new Exceptions(
                    "Debe seleccionar la sede del culto.",
                    HttpStatus.BAD_REQUEST
            );
        }

        return branchRepository.findById(resolvedId)
                .orElseThrow(() ->
                        new Exceptions("Sede no encontrada", HttpStatus.NOT_FOUND)
                );
    }

    private void assertSameOrganization(Branch branch) {

        if (!authContext.isSystem()
                && !branch.getOrganization().getId()
                        .equals(authContext.getCurrentOrganizationId())) {

            throw new Exceptions(
                    "No tiene acceso a este registro.",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private void validateForm(ChurchServiceFormRequest request) {

        if (request.getName() == null || request.getName().isBlank()) {
            throw new Exceptions(
                    "El nombre del culto es obligatorio.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private ChurchService findOrThrow(UUID id) {

        return churchServiceRepository.findById(id)
                .orElseThrow(() ->
                        new Exceptions(
                                "Culto no encontrado.",
                                HttpStatus.NOT_FOUND
                        )
                );
    }

    private Person findPersonOrThrow(UUID id) {

        return personRepository.findById(id)
                .orElseThrow(() ->
                        new Exceptions("Persona no encontrada.", HttpStatus.NOT_FOUND)
                );
    }
}
