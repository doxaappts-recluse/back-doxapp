package pe.dcs.app.features.branch_transfer;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.PersonBranch;
import pe.dcs.app.features.branch_transfer.mapper.BranchTransferMapper;
import pe.dcs.app.features.branch_transfer.request.BranchTransferHistoryRequest;
import pe.dcs.app.features.branch_transfer.request.BranchTransferRequest;
import pe.dcs.app.features.branch_transfer.request.BranchTransferSearchRequest;
import pe.dcs.app.features.branch_transfer.response.BranchTransferContextResponse;
import pe.dcs.app.features.branch_transfer.response.BranchTransferHistoryResponse;
import pe.dcs.app.features.branch_transfer.response.BranchTransferSearchRowResponse;
import pe.dcs.app.features.branch_transfer.service.BranchTransferService;
import pe.dcs.app.repository.BranchRepository;
import pe.dcs.app.repository.PersonBranchRepository;
import pe.dcs.app.repository.PersonRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.resolveSort.PersonSort;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Traslado de una persona de una sede a otra dentro de la
 * MISMA organización. No es un historial libre como Membership:
 * cada traslado cierra el PersonBranch activo (status=INACTIVE,
 * endDate) y abre uno nuevo (status=ACTIVE) en la sede destino,
 * igual que ya modela PersonBranch (ver Person.branchHistory).
 */
@Service
@RequiredArgsConstructor
public class BranchTransferServiceImpl implements BranchTransferService {

    private final PersonRepository personRepository;
    private final PersonBranchRepository personBranchRepository;
    private final BranchRepository branchRepository;
    private final BranchTransferMapper mapper;
    private final AuthContext authContext;

    // =====================================================
    // SEARCH
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BranchTransferSearchRowResponse> search(BranchTransferSearchRequest request) {

        assertCallerCanManage();

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts(),
                        PersonSort::resolvePath
                );

        Page<Person> page =
                personRepository.findAll(
                        BranchTransferSpecification.filter(request, authContext),
                        pageable
                );

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(person -> mapper.toSearchRow(
                                person,
                                findActiveBranch(person).orElse(null)
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
    public BranchTransferContextResponse getCurrent(UUID personId) {

        Person person = findPersonOrThrow(personId);

        PersonBranch current = requireActiveBranch(person);

        validateAccess(current);

        return mapper.toContextResponse(person, current);
    }

    // =====================================================
    // HISTORY
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BranchTransferHistoryResponse> history(
            UUID personId,
            BranchTransferHistoryRequest request
    ) {

        Person person = findPersonOrThrow(personId);

        PersonBranch current = requireActiveBranch(person);

        validateAccess(current);

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts(),
                        key -> "startDate".equals(key) ? "startDate" : null
                );

        Page<PersonBranch> page =
                personBranchRepository.findByPersonId(personId, pageable);

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(mapper::toHistoryResponse)
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
    // TRANSFER (cierra la sede actual, abre la destino)
    // =====================================================

    @Override
    @Transactional
    public void transfer(UUID personId, BranchTransferRequest request) {

        Person person = findPersonOrThrow(personId);

        PersonBranch current = requireActiveBranch(person);

        validateAccess(current);

        if (request.getTargetBranchId() == null) {
            throw new Exceptions(
                    "Debe indicar la sede destino.",
                    HttpStatus.BAD_REQUEST
            );
        }

        Branch targetBranch =
                branchRepository.findById(request.getTargetBranchId())
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Sede destino no encontrada.",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (targetBranch.getId().equals(current.getBranch().getId())) {
            throw new Exceptions(
                    "La persona ya pertenece a esa sede.",
                    HttpStatus.CONFLICT
            );
        }

        if (!targetBranch.getOrganization().getId()
                .equals(current.getBranch().getOrganization().getId())) {

            throw new Exceptions(
                    "La sede destino debe pertenecer a la misma organización.",
                    HttpStatus.BAD_REQUEST
            );
        }

        LocalDate transferDate = LocalDate.now();

        current.setStatus(StatusType.INACTIVE);
        current.setEndDate(transferDate);
        current.setTransferReason(request.getReason());

        personBranchRepository.save(current);

        PersonBranch next = new PersonBranch();

        next.setPerson(person);
        next.setBranch(targetBranch);
        next.setStatus(StatusType.ACTIVE);
        next.setStartDate(transferDate);
        next.setEndDate(null);

        personBranchRepository.save(next);

        person.getBranchHistory().add(next);
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private void assertCallerCanManage() {
        authContext.assertCanManageCurrent("No tiene permisos para gestionar traslados de sede.");
    }

    private void validateAccess(PersonBranch currentBranch) {

        UUID organizationId = currentBranch.getBranch().getOrganization().getId();
        UUID branchId = currentBranch.getBranch().getId();

        if (!authContext.canAccess(organizationId, branchId)) {

            throw new Exceptions(
                    "No tiene permisos para trasladar a esta persona.",
                    HttpStatus.UNAUTHORIZED
            );
        }
    }

    private PersonBranch requireActiveBranch(Person person) {

        return findActiveBranch(person)
                .orElseThrow(() ->
                        new Exceptions(
                                "La persona no tiene una sede activa.",
                                HttpStatus.CONFLICT
                        )
                );
    }

    private java.util.Optional<PersonBranch> findActiveBranch(Person person) {

        return person.getBranchHistory()
                .stream()
                .filter(pb -> pb.getStatus() == StatusType.ACTIVE)
                .findFirst();
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
