package pe.dcs.app.features.membership;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Membership;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.PersonBranch;
import pe.dcs.app.features.membership.mapper.MembershipMapper;
import pe.dcs.app.features.membership.request.MembershipFormRequest;
import pe.dcs.app.features.membership.request.MembershipHistoryRequest;
import pe.dcs.app.features.membership.request.MembershipSearchRequest;
import pe.dcs.app.features.membership.response.MembershipContextResponse;
import pe.dcs.app.features.membership.response.MembershipDetailResponse;
import pe.dcs.app.features.membership.response.MembershipSearchRowResponse;
import pe.dcs.app.features.membership.service.MembershipService;
import pe.dcs.app.repository.MembershipRepository;
import pe.dcs.app.repository.PersonRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.membership.MembershipSort;
import pe.dcs.app.util.enums.resolveSort.PersonSort;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Membresía de una persona (congregante). "current=true" marca
 * el único registro vigente por persona; crear siempre abre un
 * nuevo período (cerrando el vigente si existía), editar solo
 * modifica el registro vigente en el sitio.
 */
@Service
@RequiredArgsConstructor
public class MembershipServiceImpl implements MembershipService {

    private final PersonRepository personRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipMapper mapper;
    private final AuthContext authContext;

    // =====================================================
    // SEARCH
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MembershipSearchRowResponse> search(MembershipSearchRequest request) {

        assertCallerCanManage();

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts(),
                        PersonSort::resolvePath
                );

        Page<Person> page =
                personRepository.findAll(
                        MembershipSpecification.filter(request, authContext),
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(person -> mapper.toSearchRow(
                                person,
                                membershipRepository
                                        .findByPersonIdAndCurrentTrue(person.getId())
                                        .orElse(null),
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

    // =====================================================
    // GET CURRENT
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public MembershipContextResponse getCurrent(UUID userId) {

        Person person = findPersonOrThrow(userId);

        validateAccess(person);

        Membership current =
                membershipRepository
                        .findByPersonIdAndCurrentTrue(userId)
                        .orElse(null);

        return mapper.toContextResponse(person, current);
    }

    // =====================================================
    // CREATE (abre un nuevo período, cierra el vigente)
    // =====================================================

    @Override
    @Transactional
    public void create(UUID userId, MembershipFormRequest request) {

        Person person = findPersonOrThrow(userId);

        validateAccess(person);

        validateForm(request);

        membershipRepository
                .findByPersonIdAndCurrentTrue(userId)
                .ifPresent(previous -> closeOut(previous, request.getStartDate()));

        validateNoOverlap(userId, null, request.getStartDate(), null);
        validateSingleActive(userId, null, request.getStatus());

        Membership membership = new Membership();

        membership.setPerson(person);
        membership.setStartDate(request.getStartDate());
        membership.setEndDate(null);
        membership.setStatus(request.getStatus());
        membership.setReason(request.getReason());
        membership.setExitReason(request.getExitReason());
        membership.setNotes(request.getNotes());
        membership.setCurrent(true);

        membershipRepository.save(membership);
    }

    // =====================================================
    // UPDATE (edita el registro vigente en el sitio)
    // =====================================================

    @Override
    @Transactional
    public void update(UUID userId, UUID membershipId, MembershipFormRequest request) {

        Person person = findPersonOrThrow(userId);

        validateAccess(person);

        validateForm(request);

        Membership membership =
                membershipRepository.findById(membershipId)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Membresía no encontrada.",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (membership.getPerson() == null
                || !membership.getPerson().getId().equals(userId)) {

            throw new Exceptions(
                    "La membresía no pertenece a esta persona.",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (!Boolean.TRUE.equals(membership.getCurrent())) {

            throw new Exceptions(
                    "Solo se puede editar el registro de membresía vigente.",
                    HttpStatus.CONFLICT
            );
        }

        validateNoOverlap(userId, membership.getId(), request.getStartDate(), membership.getEndDate());
        validateSingleActive(userId, membership.getId(), request.getStatus());

        membership.setStartDate(request.getStartDate());
        membership.setStatus(request.getStatus());
        membership.setReason(request.getReason());
        membership.setExitReason(request.getExitReason());
        membership.setNotes(request.getNotes());

        membershipRepository.save(membership);
    }

    // =====================================================
    // HISTORY
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MembershipDetailResponse> history(
            UUID userId,
            MembershipHistoryRequest request
    ) {

        Person person = findPersonOrThrow(userId);

        validateAccess(person);

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts(),
                        MembershipSort::resolvePath
                );

        Page<Membership> page =
                membershipRepository.findByPersonId(userId, pageable);

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(mapper::toDetailResponse)
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
    // HELPERS
    // =====================================================

    private void closeOut(Membership previous, LocalDate newStartDate) {

        previous.setCurrent(false);

        if (previous.getEndDate() == null) {

            LocalDate closingDate =
                    newStartDate.minusDays(1);

            if (closingDate.isBefore(previous.getStartDate())) {
                closingDate = previous.getStartDate();
            }

            previous.setEndDate(closingDate);
        }

        membershipRepository.save(previous);
    }

    /**
     * Es un registro histórico: los periodos de membresía de
     * una misma persona no pueden solaparse en fechas (igual
     * que en contratos). endDate=null significa periodo abierto
     * (sigue vigente).
     */
    private void validateNoOverlap(
            UUID personId,
            UUID excludeMembershipId,
            LocalDate startDate,
            LocalDate endDate
    ) {

        List<Membership> history =
                membershipRepository.findByPersonIdOrderByStartDateDesc(personId);

        for (Membership existing : history) {

            if (excludeMembershipId != null
                    && excludeMembershipId.equals(existing.getId())) {
                continue;
            }

            LocalDate existingStart = existing.getStartDate();
            LocalDate existingEnd = existing.getEndDate();

            boolean overlaps =
                    (endDate == null || !endDate.isBefore(existingStart))
                            && (existingEnd == null || !startDate.isAfter(existingEnd));

            if (overlaps) {

                throw new Exceptions(
                        "El periodo se solapa con otro registro de membresía de la persona.",
                        HttpStatus.CONFLICT
                );
            }
        }
    }

    /**
     * Solo puede haber un registro en estado ACTIVE por persona
     * (es un registro histórico: los periodos cerrados no deben
     * quedar como vigentes/activos).
     */
    private void validateSingleActive(
            UUID personId,
            UUID excludeMembershipId,
            StatusType status
    ) {

        if (status != StatusType.ACTIVE) {
            return;
        }

        boolean alreadyActive =
                membershipRepository.findByPersonIdOrderByStartDateDesc(personId)
                        .stream()
                        .anyMatch(m ->
                                m.getStatus() == StatusType.ACTIVE
                                        && (excludeMembershipId == null
                                                || !m.getId().equals(excludeMembershipId))
                        );

        if (alreadyActive) {

            throw new Exceptions(
                    "Ya existe un registro de membresía en estado activo para esta persona. Solo puede haber una membresía activa.",
                    HttpStatus.CONFLICT
            );
        }
    }

    private void validateForm(MembershipFormRequest request) {

        if (request.getStartDate() == null) {
            throw new Exceptions(
                    "La fecha de inicio es obligatoria.",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (request.getStatus() == null) {
            throw new Exceptions(
                    "El estado es obligatorio.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void assertCallerCanManage() {

        if (!authContext.isSystem()
                && !authContext.isCurrentOrganizationAdmin()
                && !authContext.isCurrentBranchAdmin()) {

            throw new Exceptions(
                    "No tiene permisos para gestionar membresías.",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private void validateAccess(Person person) {

        PersonBranch activeBranch =
                person.getBranchHistory()
                        .stream()
                        .filter(pb -> pb.getStatus() == StatusType.ACTIVE)
                        .findFirst()
                        .orElse(null);

        if (activeBranch == null) {
            throw new Exceptions(
                    "La persona no tiene una sede activa.",
                    HttpStatus.CONFLICT
            );
        }

        UUID organizationId =
                activeBranch.getBranch().getOrganization().getId();

        UUID branchId =
                activeBranch.getBranch().getId();

        if (!authContext.canAccess(organizationId, branchId)) {

            throw new Exceptions(
                    "No tiene permisos para gestionar la membresía de esta persona.",
                    HttpStatus.UNAUTHORIZED
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

}
