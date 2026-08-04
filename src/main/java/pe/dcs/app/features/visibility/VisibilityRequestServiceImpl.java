package pe.dcs.app.features.visibility;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.DataAccessRule;
import pe.dcs.app.entity.Module;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.PersonBranch;
import pe.dcs.app.entity.VisibilityGrant;
import pe.dcs.app.entity.VisibilityRequest;
import pe.dcs.app.features.visibility.mapper.VisibilityRequestMapper;
import pe.dcs.app.features.visibility.request.VisibilityRequestApproveRequest;
import pe.dcs.app.features.visibility.request.VisibilityRequestCreateRequest;
import pe.dcs.app.features.visibility.request.VisibilityRequestSearchRequest;
import pe.dcs.app.features.visibility.response.PersonBranchOptionResponse;
import pe.dcs.app.features.visibility.response.VisibilityRequestPersonResponse;
import pe.dcs.app.features.visibility.response.VisibilityRequestRowResponse;
import pe.dcs.app.features.visibility.service.VisibilityRequestService;
import pe.dcs.app.repository.BranchRepository;
import pe.dcs.app.repository.DataAccessRuleRepository;
import pe.dcs.app.repository.ModuleRepository;
import pe.dcs.app.repository.PersonBranchRepository;
import pe.dcs.app.repository.PersonRepository;
import pe.dcs.app.repository.VisibilityGrantRepository;
import pe.dcs.app.repository.VisibilityRequestRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.rules.DataScope;
import pe.dcs.app.util.enums.rules.VisibilityStatus;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Solicitudes de visibilidad entre sedes: cuando una persona se
 * traslada, la sede que la recibe puede pedir ver la data
 * histórica (Membresía/Servicio Ministerial/Bautizo) que quedó
 * "dueña" de la sede anterior — ver Membership.branch,
 * MinistryAssignment.branch, Baptism.branch. Solo aplica a
 * módulos con DataAccessRule.scope = APPROVAL_REQUIRED.
 */
@Service
@RequiredArgsConstructor
public class VisibilityRequestServiceImpl implements VisibilityRequestService {

    private final VisibilityRequestRepository visibilityRequestRepository;
    private final VisibilityGrantRepository visibilityGrantRepository;
    private final DataAccessRuleRepository dataAccessRuleRepository;
    private final PersonRepository personRepository;
    private final PersonBranchRepository personBranchRepository;
    private final BranchRepository branchRepository;
    private final ModuleRepository moduleRepository;
    private final VisibilityRequestMapper mapper;
    private final VisibilityGuard visibilityGuard;
    private final AuthContext authContext;

    // =====================================================
    // SEARCH
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VisibilityRequestRowResponse> searchIncoming(VisibilityRequestSearchRequest request) {
        return search(request, VisibilityRequestSpecification.Direction.INCOMING);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VisibilityRequestRowResponse> searchOutgoing(VisibilityRequestSearchRequest request) {
        return search(request, VisibilityRequestSpecification.Direction.OUTGOING);
    }

    private PageResponse<VisibilityRequestRowResponse> search(
            VisibilityRequestSearchRequest request,
            VisibilityRequestSpecification.Direction direction
    ) {

        assertCallerHasContext();

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        Page<VisibilityRequest> page =
                visibilityRequestRepository.findAll(
                        VisibilityRequestSpecification.filter(request, authContext, direction),
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(vr -> mapper.toRow(
                                vr,
                                showAudit,
                                direction == VisibilityRequestSpecification.Direction.INCOMING
                                        && vr.getStatus() == VisibilityStatus.PENDING
                                        && visibilityGuard.canApprove(vr.getSourceBranch(), vr.getModule().getCode())
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

    // =====================================================
    // BUSCAR PERSONA POR DNI (para armar la solicitud)
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public VisibilityRequestPersonResponse findPersonByDni(String dni) {

        assertCallerHasContext();

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

        return new VisibilityRequestPersonResponse(
                person.getId(),
                person.getName(),
                person.getLastname()
        );
    }

    // =====================================================
    // SEDES DISPONIBLES PARA PEDIR (historial de la persona)
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public List<PersonBranchOptionResponse> getPersonBranches(UUID personId) {

        Person person = findPersonOrThrow(personId);

        UUID currentBranchId =
                person.getBranchHistory()
                        .stream()
                        .filter(pb -> pb.getStatus() == StatusType.ACTIVE)
                        .map(pb -> pb.getBranch().getId())
                        .findFirst()
                        .orElse(null);

        Map<UUID, PersonBranchOptionResponse> byBranch = new LinkedHashMap<>();

        List<PersonBranch> history =
                personBranchRepository.findByPersonIdOrderByStartDateDesc(personId);

        for (PersonBranch pb : history) {

            if (pb.getBranch() == null) {
                continue;
            }

            UUID branchId = pb.getBranch().getId();

            if (branchId.equals(currentBranchId)) {
                continue;
            }

            byBranch.putIfAbsent(
                    branchId,
                    new PersonBranchOptionResponse(
                            branchId,
                            pb.getBranch().getName(),
                            pb.getStartDate(),
                            pb.getEndDate()
                    )
            );
        }

        return byBranch.values()
                .stream()
                .sorted(Comparator.comparing(
                        PersonBranchOptionResponse::getStartDate,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }

    // =====================================================
    // CREATE
    // =====================================================

    @Override
    @Transactional
    public void create(UUID personId, VisibilityRequestCreateRequest request) {

        Person person = findPersonOrThrow(personId);

        UUID requestBranchId = authContext.getCurrentBranchId();

        if (requestBranchId == null) {
            throw new Exceptions(
                    "Debe estar operando en el contexto de una sede para solicitar visibilidad.",
                    HttpStatus.CONFLICT
            );
        }

        if (request.getModuleCode() == null || request.getModuleCode().isBlank()) {
            throw new Exceptions(
                    "El módulo es obligatorio.",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (request.getSourceBranchId() == null) {
            throw new Exceptions(
                    "La sede de origen es obligatoria.",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (request.getSourceBranchId().equals(requestBranchId)) {
            throw new Exceptions(
                    "No puede solicitar visibilidad sobre su propia sede.",
                    HttpStatus.BAD_REQUEST
            );
        }

        Module module =
                moduleRepository.findByCodeAndStatus(
                        request.getModuleCode(),
                        StatusType.ACTIVE
                ).orElseThrow(() ->
                        new Exceptions(
                                "Módulo no encontrado.",
                                HttpStatus.NOT_FOUND
                        )
                );

        DataAccessRule rule =
                dataAccessRuleRepository
                        .findByModule_CodeAndEnabledTrue(request.getModuleCode())
                        .orElse(null);

        if (rule == null || rule.getScope() != DataScope.APPROVAL_REQUIRED) {
            throw new Exceptions(
                    "Este módulo no requiere solicitud de visibilidad.",
                    HttpStatus.BAD_REQUEST
            );
        }

        Branch sourceBranch =
                branchRepository.findById(request.getSourceBranchId())
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Sede de origen no encontrada.",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        Branch requestBranch =
                branchRepository.findById(requestBranchId)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Sede actual no encontrada.",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (!sourceBranch.getOrganization().getId()
                .equals(requestBranch.getOrganization().getId())) {

            throw new Exceptions(
                    "La sede de origen debe pertenecer a la misma organización.",
                    HttpStatus.BAD_REQUEST
            );
        }

        boolean alreadyPending =
                !visibilityRequestRepository.findByPerson_IdAndSourceBranch_IdAndModule_CodeAndStatus(
                        personId,
                        sourceBranch.getId(),
                        request.getModuleCode(),
                        VisibilityStatus.PENDING
                ).isEmpty();

        if (alreadyPending) {
            throw new Exceptions(
                    "Ya existe una solicitud pendiente para esta persona, sede y módulo.",
                    HttpStatus.CONFLICT
            );
        }

        Person requestedBy = findPersonOrThrow(authContext.getUserId());

        VisibilityRequest visibilityRequest = new VisibilityRequest();

        visibilityRequest.setPerson(person);
        visibilityRequest.setRequestBranch(requestBranch);
        visibilityRequest.setSourceBranch(sourceBranch);
        visibilityRequest.setModule(module);
        visibilityRequest.setRequestedBy(requestedBy);
        visibilityRequest.setReason(request.getReason());
        visibilityRequest.setRequestedFrom(request.getRequestedFrom());
        visibilityRequest.setRequestedUntil(request.getRequestedUntil());
        visibilityRequest.setStatus(VisibilityStatus.PENDING);

        visibilityRequestRepository.save(visibilityRequest);
    }

    // =====================================================
    // APPROVE / REJECT
    // =====================================================

    @Override
    @Transactional
    public void approve(UUID requestId, VisibilityRequestApproveRequest request) {

        VisibilityRequest visibilityRequest = findRequestOrThrow(requestId);

        assertPending(visibilityRequest);

        if (!visibilityGuard.canApprove(
                visibilityRequest.getSourceBranch(),
                visibilityRequest.getModule().getCode()
        )) {

            throw new Exceptions(
                    "No tiene permisos para aprobar solicitudes de la sede dueña de esta data.",
                    HttpStatus.FORBIDDEN
            );
        }

        Person approver = findPersonOrThrow(authContext.getUserId());

        LocalDate approvedUntil =
                request != null
                        ? request.getApprovedUntil()
                        : null;

        visibilityRequest.setStatus(VisibilityStatus.APPROVED);
        visibilityRequest.setApprovedAt(Instant.now());
        visibilityRequest.setApprovedBy(approver);
        visibilityRequest.setApprovedUntil(approvedUntil);

        visibilityRequestRepository.save(visibilityRequest);

        VisibilityGrant grant =
                visibilityGrantRepository.findActive(
                        visibilityRequest.getPerson().getId(),
                        visibilityRequest.getSourceBranch().getId(),
                        visibilityRequest.getRequestBranch().getId(),
                        visibilityRequest.getModule().getCode()
                ).orElseGet(VisibilityGrant::new);

        grant.setPerson(visibilityRequest.getPerson());
        grant.setSourceBranch(visibilityRequest.getSourceBranch());
        grant.setTargetBranch(visibilityRequest.getRequestBranch());
        grant.setModule(visibilityRequest.getModule());
        grant.setVisibleUntil(approvedUntil);
        grant.setActive(true);
        grant.setApprovedBy(approver);
        grant.setApprovedAt(Instant.now());

        visibilityGrantRepository.save(grant);
    }

    @Override
    @Transactional
    public void reject(UUID requestId) {

        VisibilityRequest visibilityRequest = findRequestOrThrow(requestId);

        assertPending(visibilityRequest);

        if (!visibilityGuard.canApprove(
                visibilityRequest.getSourceBranch(),
                visibilityRequest.getModule().getCode()
        )) {

            throw new Exceptions(
                    "No tiene permisos para rechazar solicitudes de la sede dueña de esta data.",
                    HttpStatus.FORBIDDEN
            );
        }

        Person approver = findPersonOrThrow(authContext.getUserId());

        visibilityRequest.setStatus(VisibilityStatus.REJECTED);
        visibilityRequest.setRejectedAt(Instant.now());
        visibilityRequest.setRejectedBy(approver);

        visibilityRequestRepository.save(visibilityRequest);
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private void assertPending(VisibilityRequest visibilityRequest) {

        if (visibilityRequest.getStatus() != VisibilityStatus.PENDING) {

            throw new Exceptions(
                    "Esta solicitud ya fue resuelta.",
                    HttpStatus.CONFLICT
            );
        }
    }

    private void assertCallerHasContext() {

        if (!authContext.isSystem() && authContext.getCurrentOrganizationId() == null) {

            throw new Exceptions(
                    "No tiene un contexto de organización activo.",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private Person findPersonOrThrow(UUID id) {

        return personRepository.findById(id)
                .orElseThrow(() ->
                        new Exceptions(
                                "Persona no encontrada.",
                                HttpStatus.NOT_FOUND
                        )
                );
    }

    private VisibilityRequest findRequestOrThrow(UUID id) {

        return visibilityRequestRepository.findById(id)
                .orElseThrow(() ->
                        new Exceptions(
                                "Solicitud de visibilidad no encontrada.",
                                HttpStatus.NOT_FOUND
                        )
                );
    }
}
