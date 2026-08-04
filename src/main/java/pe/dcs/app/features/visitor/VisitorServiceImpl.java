package pe.dcs.app.features.visitor;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.Membership;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.Visitor;
import pe.dcs.app.features.visitor.mapper.VisitorMapper;
import pe.dcs.app.features.visitor.request.VisitorConvertToMemberRequest;
import pe.dcs.app.features.visitor.request.VisitorFormRequest;
import pe.dcs.app.features.visitor.request.VisitorSearchRequest;
import pe.dcs.app.features.visitor.response.VisitorDetailResponse;
import pe.dcs.app.features.visitor.response.VisitorSearchRowResponse;
import pe.dcs.app.features.visitor.service.VisitorService;
import pe.dcs.app.repository.BranchRepository;
import pe.dcs.app.repository.MembershipRepository;
import pe.dcs.app.repository.PersonRepository;
import pe.dcs.app.repository.VisitorRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.membership.MembershipReason;
import pe.dcs.app.util.enums.visitor.VisitorConsolidationStage;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Datos específicos de "ser visitante" (cómo llegó, etapa de
 * consolidación, conversión a miembro) sobre una Person que ya
 * existe en el sistema (creada vía el módulo Usuarios, igual patrón
 * que Bautizo/Membresía). El seguimiento en sí (contactos, líder
 * asignado, peticiones de oración) es responsabilidad de
 * features.pastoral_followup — este service no lo toca.
 */
@Service
@RequiredArgsConstructor
public class VisitorServiceImpl implements VisitorService {

    private final PersonRepository personRepository;
    private final BranchRepository branchRepository;
    private final VisitorRepository visitorRepository;
    private final MembershipRepository membershipRepository;
    private final VisitorMapper mapper;
    private final AuthContext authContext;
    private final VisitorAccessGuard accessGuard;

    // =====================================================
    // SEARCH
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VisitorSearchRowResponse> search(VisitorSearchRequest request) {

        accessGuard.assertCanUse();

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        Page<Visitor> page =
                visitorRepository.findAll(
                        VisitorSpecification.filter(request.getFilters(), authContext),
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(v -> mapper.toSearchRow(v, showAudit))
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
    // GET
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public VisitorDetailResponse getByPersonId(UUID personId) {

        accessGuard.assertCanUse();

        Visitor visitor = findByPersonOrThrow(personId);

        return mapper.toDetailResponse(visitor, isActiveMember(visitor.getPerson()));
    }

    // =====================================================
    // CREATE
    // =====================================================

    @Override
    @Transactional
    public void create(UUID personId, VisitorFormRequest request) {

        accessGuard.assertCanCreate();

        Person person = findPersonOrThrow(personId);

        if (visitorRepository.existsByPersonId(personId)) {
            throw new Exceptions(
                    "Esta persona ya tiene un registro de visitante.",
                    HttpStatus.CONFLICT
            );
        }

        validateForm(request);

        Branch branch = resolveBranch(request.getBranchId());

        Visitor visitor = new Visitor();

        visitor.setPerson(person);
        applyForm(visitor, request, branch);

        visitorRepository.save(visitor);
    }

    // =====================================================
    // UPDATE
    // =====================================================

    @Override
    @Transactional
    public void update(UUID personId, VisitorFormRequest request) {

        validateForm(request);

        Visitor visitor = findByPersonOrThrow(personId);

        accessGuard.assertCanManage(visitor.getBranch());

        Branch branch =
                request.getBranchId() != null
                        ? resolveBranch(request.getBranchId())
                        : visitor.getBranch();

        applyForm(visitor, request, branch);

        visitorRepository.save(visitor);
    }

    // =====================================================
    // CONVERTIR A MIEMBRO
    // =====================================================

    @Override
    @Transactional
    public void convertToMember(UUID personId, VisitorConvertToMemberRequest request) {

        Visitor visitor = findByPersonOrThrow(personId);

        accessGuard.assertCanManage(visitor.getBranch());

        Person person = visitor.getPerson();

        if (isActiveMember(person)) {
            throw new Exceptions(
                    "Esta persona ya tiene una membresía activa.",
                    HttpStatus.CONFLICT
            );
        }

        if (request.getStartDate() == null) {
            throw new Exceptions(
                    "La fecha de inicio de membresía es obligatoria.",
                    HttpStatus.BAD_REQUEST
            );
        }

        Membership membership = new Membership();

        membership.setPerson(person);
        membership.setStartDate(request.getStartDate());
        membership.setEndDate(null);
        membership.setStatus(StatusType.ACTIVE);
        membership.setReason(
                request.getReason() != null
                        ? request.getReason()
                        : MembershipReason.MEMBERSHIP
        );
        membership.setNotes(request.getNotes());
        membership.setCurrent(true);
        membership.setBranch(visitor.getBranch());

        membershipRepository.save(membership);

        visitor.setConsolidationStage(VisitorConsolidationStage.CONVERTED);
        visitor.setConvertedAt(LocalDate.now());

        visitorRepository.save(visitor);
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private void applyForm(Visitor visitor, VisitorFormRequest request, Branch branch) {

        visitor.setFirstVisitDate(request.getFirstVisitDate());
        visitor.setHowArrived(request.getHowArrived());
        visitor.setInvitedBy(
                request.getInvitedByPersonId() != null
                        ? findPersonOrThrow(request.getInvitedByPersonId())
                        : null
        );
        visitor.setConsolidationStage(request.getConsolidationStage());
        visitor.setNotes(request.getNotes());
        visitor.setBranch(branch);
    }

    private boolean isActiveMember(Person person) {

        return person != null
                && membershipRepository.existsByPersonIdAndCurrentTrueAndStatus(
                        person.getId(),
                        StatusType.ACTIVE
                );
    }

    private Branch resolveBranch(UUID requestedBranchId) {

        UUID branchId = accessGuard.resolveBranchId(requestedBranchId);

        if (branchId == null) {
            throw new Exceptions(
                    "Debe seleccionar la sede del visitante.",
                    HttpStatus.BAD_REQUEST
            );
        }

        return branchRepository.findById(branchId)
                .orElseThrow(() ->
                        new Exceptions("Sede no encontrada", HttpStatus.NOT_FOUND)
                );
    }

    private void validateForm(VisitorFormRequest request) {

        if (request.getFirstVisitDate() == null) {
            throw new Exceptions(
                    "La fecha de primera visita es obligatoria.",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (request.getHowArrived() == null) {
            throw new Exceptions(
                    "Cómo llegó el visitante es obligatorio.",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (request.getConsolidationStage() == null) {
            throw new Exceptions(
                    "La etapa de consolidación es obligatoria.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private Visitor findByPersonOrThrow(UUID personId) {

        return visitorRepository.findByPersonId(personId)
                .orElseThrow(() ->
                        new Exceptions(
                                "Esta persona no tiene un registro de visitante.",
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
