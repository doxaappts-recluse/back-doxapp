package pe.dcs.app.features.branch;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.Organization;
import pe.dcs.app.features.branch.mapper.BranchMapper;
import pe.dcs.app.features.branch.request.BranchCreateRequest;
import pe.dcs.app.features.branch.request.BranchListRequest;
import pe.dcs.app.features.branch.request.BranchUpdateRequest;
import pe.dcs.app.features.branch.response.BranchListResponse;
import pe.dcs.app.features.branch.response.BranchResponse;
import pe.dcs.app.features.branch.service.BranchService;
import pe.dcs.app.repository.BranchRepository;
import pe.dcs.app.repository.ContractRepository;
import pe.dcs.app.repository.OrganizationRepository;
import pe.dcs.app.util.DateUtils;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.contract.ContractStatus;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BranchServiceImpl implements BranchService {

    private final BranchRepository repository;
    private final ContractRepository contractRepository;
    private final OrganizationRepository organizationRepository;

    @Override
    public PageResponse<BranchResponse> findByOrganization(
            UUID organizationId,
            BranchListRequest request
    ) {

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        var page =
                repository.findAll(
                                BranchSpecification.filter(
                                        organizationId,
                                        request
                                ),
                                pageable
                        )
                        .map(BranchMapper::toResponse);

        return new PageResponse<>(
                page.getContent(),
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
    public BranchResponse create(
            UUID organizationId,
            BranchCreateRequest request
    ) {

        Organization organization =
                organizationRepository.findById(organizationId)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "No se encontró la organización.",
                                        HttpStatus.NOT_FOUND
                                ));

        validateCodeForCreate(
                request.getCode(),
                organizationId
        );

        Branch branch = new Branch();

        branch.setOrganization(organization);
        branch.setName(request.getName());
        branch.setCode(request.getCode());
        branch.setAddress(request.getAddress());
        branch.setPhone(request.getPhone());
        branch.setEmail(request.getEmail());
        branch.setOpeningDate(request.getOpeningDate());

        branch.setMain(false);
        branch.setStatus(StatusType.ACTIVE);

        repository.save(branch);

        return BranchMapper.toResponse(branch);
    }

    @Override
    @Transactional
    public BranchResponse update(
            UUID id,
            BranchUpdateRequest request
    ) {

        Branch branch = getBranch(id);

        validateCodeForUpdate(
                request.getCode(),
                branch.getOrganization().getId(),
                id
        );

        branch.setName(request.getName());
        branch.setCode(request.getCode());
        branch.setAddress(request.getAddress());
        branch.setPhone(request.getPhone());
        branch.setEmail(request.getEmail());
        branch.setOpeningDate(request.getOpeningDate());

        repository.save(branch);

        return BranchMapper.toResponse(branch);
    }

    @Override
    @Transactional
    public void enable(UUID id) {

        Branch branch = getBranch(id);

        if (branch.getStatus() == StatusType.ACTIVE) {
            return;
        }

        if (branch.getOrganization().getStatus() == StatusType.INACTIVE) {

            throw new Exceptions(
                    "La organización se encuentra deshabilitada.",
                    HttpStatus.CONFLICT
            );
        }

        branch.setStatus(StatusType.ACTIVE);

        repository.save(branch);
    }

    @Override
    @Transactional
    public void disable(UUID id) {

        Branch branch = getBranch(id);

        if (branch.getStatus() == StatusType.INACTIVE) {
            return;
        }

        if (Boolean.TRUE.equals(branch.getMain())) {

            throw new Exceptions(
                    "No se puede deshabilitar la sede principal de la organización.",
                    HttpStatus.CONFLICT
            );
        }

        LocalDate today = DateUtils.utcToday();

        boolean hasActiveContract =
                contractRepository
                        .existsByBranchIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                                id,
                                ContractStatus.ACTIVE,
                                today,
                                today
                        );

        if (hasActiveContract) {

            throw new Exceptions(
                    "No se puede deshabilitar la sede porque tiene un contrato vigente.",
                    HttpStatus.CONFLICT
            );
        }

        branch.setStatus(StatusType.INACTIVE);

        repository.save(branch);
    }

    @Override
    @Transactional
    public void changeMain(UUID id) {

        Branch branch = getBranch(id);

        if (branch.getStatus() == StatusType.INACTIVE) {

            throw new Exceptions(
                    "No se puede establecer como principal una sede deshabilitada.",
                    HttpStatus.CONFLICT
            );
        }

        if (branch.getOrganization().getStatus() == StatusType.INACTIVE) {

            throw new Exceptions(
                    "No se puede cambiar la sede principal porque la organización está deshabilitada.",
                    HttpStatus.CONFLICT
            );
        }

        if (Boolean.TRUE.equals(branch.getMain())) {
            return;
        }

        repository.clearMainBranch(
                branch.getOrganization().getId()
        );

        branch.setMain(true);

        repository.save(branch);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchListResponse> findByOrganization(UUID organizationId) {

        List<Branch> branches =
                repository
                        .findByOrganizationIdAndStatusOrderByNameAsc(
                                organizationId,
                                StatusType.ACTIVE
                        );

        return branches
                .stream()
                .map(
                        branch ->
                                BranchListResponse
                                        .builder()
                                        .id(
                                                branch.getId()
                                        )
                                        .name(
                                                branch.getName()
                                        )
                                        .build()
                )
                .toList();
    }

    private void validateCodeForCreate(
            String code,
            UUID organizationId
    ) {

        if (repository.existsByCodeAndOrganizationId(code, organizationId)) {

            throw new Exceptions(
                    "Ya existe una sede con ese código dentro de la organización.",
                    HttpStatus.CONFLICT
            );
        }
    }

    private void validateCodeForUpdate(
            String code,
            UUID organizationId,
            UUID id
    ) {

        if (repository.existsByCodeAndOrganizationIdAndIdNot(
                code,
                organizationId,
                id
        )) {

            throw new Exceptions(
                    "Ya existe una sede con ese código dentro de la organización.",
                    HttpStatus.CONFLICT
            );
        }
    }

    private Branch getBranch(UUID id) {

        return repository.findById(id)
                .orElseThrow(() ->
                        new Exceptions(
                                "Branch not found",
                                HttpStatus.NOT_FOUND
                        ));
    }
}