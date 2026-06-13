package pe.dcs.app.features.contract;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.contract.service.ContractModuleService;
import pe.dcs.app.features.contract.service.ContractService;
import pe.dcs.app.entity.Contract;
import pe.dcs.app.entity.Organization;
import pe.dcs.app.features.contract.request.ContractCreateRequest;
import pe.dcs.app.features.contract.request.ContractFilterRequest;
import pe.dcs.app.features.contract.request.ContractListRequest;
import pe.dcs.app.features.contract.response.ContractModuleResponse;
import pe.dcs.app.features.contract.response.ContractResponse;
import pe.dcs.app.features.contract.response.ContractResponseSearch;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;
import pe.dcs.app.repository.ContractRepository;
import pe.dcs.app.repository.OrganizationRepository;
import pe.dcs.app.features.contract.mapper.ContractMapper;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.enums.contract.ContractRenewalType;
import pe.dcs.app.util.enums.contract.ContractStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class ContractServiceImpl implements ContractService {

    private final ContractRepository contractRepository;
    private final OrganizationRepository organizationRepository;
    private final ContractModuleService contractModuleService;

    @Override
    @Transactional
    public void process(ContractCreateRequest request) {

        // 1. LOCK POR ORGANIZACIÓN (CLAVE CONCURRENCIA)
        contractRepository.lockByOrganization(request.getOrganizationId());

        // 2. ORGANIZACIÓN
        Organization organization =
                organizationRepository.findById(request.getOrganizationId())
                        .orElseThrow(() -> new Exceptions(
                                "Organization not found",
                                HttpStatus.NOT_FOUND
                        ));

        // =========================================================
        // 3. CONTRATOS EXISTENTES
        // =========================================================

        List<Contract> existing =
                getActiveAndPendingContracts(
                        request.getOrganizationId()
                );

        Contract activeContract =
                getActiveContract(existing);

        // =========================================================
        // 4. DETERMINAR SI REEMPLAZA ACTIVE
        // =========================================================

        boolean replaceActive =
                request.getRenewalType() == ContractRenewalType.UPGRADE
                        || request.getRenewalType() == ContractRenewalType.DOWNGRADE;

        if (replaceActive && activeContract == null) {
            throw new Exceptions(
                    "No active contract found for upgrade/downgrade",
                    HttpStatus.CONFLICT
            );
        }

        // =========================================================
        // 5. VALIDAR OVERLAP
        // =========================================================

        Contract candidate = new Contract();
        candidate.setStartDate(request.getStartDate());
        candidate.setEndDate(request.getEndDate());

        validateNoContractOverlap(
                request.getOrganizationId(),
                candidate,
                replaceActive
                        ? activeContract.getId()
                        : null
        );

        // =========================================================
        // 6. EXPIRAR ACTIVE
        // =========================================================

        if (replaceActive) {

            activeContract.expireManually();

            contractRepository.save(activeContract);
        }

        // =========================================================
        // 7. EXPIRAR ACTIVE (UPGRADE/DOWNGRADE)
        // =========================================================

        if (replaceActive) {

            activeContract.expireManually();

            contractRepository.save(activeContract);
        }

        // =========================================================
        // 8. CREAR CONTRATO
        // =========================================================

        Contract contract = new Contract();

        contract.setOrganization(organization);
        contract.setPlanName(request.getPlanName());
        contract.setPrice(request.getPrice());
        contract.setCurrency(request.getCurrency());
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setNumberUsers(request.getNumberUsers());
        contract.setRenewalType(request.getRenewalType());
        contract.setCreatedAt(LocalDateTime.now());

        if (replaceActive) {
            contract.setPreviousContractId(activeContract.getId());
        }

        contract.setStatus(
                request.getStartDate().isAfter(LocalDate.now())
                        ? ContractStatus.PENDING
                        : ContractStatus.ACTIVE
        );

        contractRepository.save(contract);

        // =========================================================
        // 9. MODULES
        // =========================================================

        contractModuleService.saveModules(contract, request.getModules());
    }

    @Override
    public PageResponse<ContractResponseSearch> search(
            ContractListRequest request
    ) {

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        Page<Contract> page;

        boolean hasOrganizationFilter =
                request.getFilters() != null
                        && request.getFilters().getOrganizationId() != null;

        if (hasOrganizationFilter) {

            page =
                    contractRepository.findAll(
                            ContractSpecification.filter(
                                    request.getFilters()
                            ),
                            pageable
                    );

        } else {

            page = contractRepository.findRepresentativeContracts(buildRepresentativePageable(pageable));
        }

        List<ContractResponseSearch> content =
                page.getContent()
                        .stream()
                        .map(ContractMapper::toResponseSearch)
                        .toList();

        return new PageResponse<>(
                content,
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    private Pageable buildRepresentativePageable(Pageable pageable) {

        // whitelist de campos permitidos en representative query
        Map<String, String> SORT_MAPPING = Map.of(
                "organizationName", "organization_name",
                "planName", "plan_name",
                "currency", "currency",
                "status", "status",
                "startDate", "start_date",
                "endDate", "end_date",
                "createdAt", "created_at",
                "price", "price"
        );

        List<Sort.Order> mapped =
                pageable.getSort()
                        .stream()
                        .map(o -> {

                            String mappeds = SORT_MAPPING.get(o.getProperty());

                            if (mappeds == null) {
                                return null;
                            }

                            return new Sort.Order(
                                    o.getDirection(),
                                    mappeds
                            );
                        })
                        .filter(Objects::nonNull)
                        .toList();

        if (mapped.isEmpty()) {
            // default stable sort
            return PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize()
            );
        }

        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(mapped)
        );
    }

    @Override
    public PageResponse<ContractResponseSearch> history(
            UUID organizationId,
            ContractListRequest request
    ) {

        if (request.getFilters() == null) {
            request.setFilters(
                    new ContractFilterRequest()
            );
        }

        request.getFilters()
                .setOrganizationId(organizationId);

        return search(request);
    }

    @Override
    public void suspend(UUID id) {
        Contract contract = getContract(id);
        contract.suspend();
        contractRepository.save(contract);
    }

    @Override
    @Transactional
    public void reactivate(UUID id) {

        Contract contract =
                contractRepository.findByIdForUpdate(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Contract not found",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        UUID organizationId =
                contract.getOrganization().getId();

        contractRepository.lockByOrganization(
                organizationId
        );

        LocalDate today = LocalDate.now();

        if (today.isAfter(contract.getEndDate())) {
            throw new Exceptions(
                    "Contract is expired",
                    HttpStatus.CONFLICT
            );
        }

        if (today.isBefore(contract.getStartDate())) {
            throw new Exceptions(
                    "Contract has not started yet",
                    HttpStatus.CONFLICT
            );
        }

        validateNoContractOverlap(
                organizationId,
                contract,
                contract.getId()
        );

        contract.activate();

        contractRepository.save(contract);
    }

    @Override
    public void cancel(UUID id) {
        Contract contract = getContract(id);
        contract.cancel();
        contractRepository.save(contract);
    }

    @Override
    @Transactional
    public void activate(UUID id) {

        Contract contract =
                contractRepository.findByIdForUpdate(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Contract not found",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        UUID organizationId =
                contract.getOrganization().getId();

        contractRepository.lockByOrganization(
                organizationId
        );

        LocalDate today = LocalDate.now();

        if (!today.equals(contract.getStartDate())) {
            throw new Exceptions(
                    "Contract can only be activated on its start date",
                    HttpStatus.CONFLICT
            );
        }

        if (today.isAfter(contract.getEndDate())) {
            throw new Exceptions(
                    "Contract is expired",
                    HttpStatus.CONFLICT
            );
        }

        validateNoContractOverlap(
                organizationId,
                contract,
                contract.getId()
        );

        contract.activateManually();

        contractRepository.save(contract);
    }

    @Override
    @Transactional(readOnly = true)
    public ContractResponse getBaseContract(UUID organizationId) {

        LocalDate now = LocalDate.now();

        // =========================================================
        // 1. ACTIVE ACTUAL
        // =========================================================

        Optional<Contract> active =
                contractRepository
                        .findTopByOrganizationIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByEndDateDesc(
                                organizationId,
                                ContractStatus.ACTIVE,
                                now,
                                now
                        );

        Contract contract;

        if (active.isPresent()) {

            contract = active.get();

        } else {

            // =========================================================
            // 2. FALLBACK: ÚLTIMO CONTRATO HISTÓRICO
            // =========================================================

            contract =
                    contractRepository
                            .findTopByOrganizationIdOrderByEndDateDesc(organizationId)
                            .orElseThrow(() ->
                                    new Exceptions(
                                            "No contracts found for organization",
                                            HttpStatus.NOT_FOUND
                                    )
                            );
        }

        // =========================================================
        // 3. MODULES
        // =========================================================

        List<ContractModuleResponse> modules =
                contractModuleService.getModules(contract.getId());

        return ContractMapper.toResponse(contract, modules);
    }

    private Contract getContract(UUID id) {
        return contractRepository.findById(id)
                .orElseThrow(() -> new Exceptions("Contract not found", HttpStatus.NOT_FOUND));
    }

    private List<Contract> getActiveAndPendingContracts(
            UUID organizationId
    ) {

        return contractRepository.findByOrganizationIdAndStatusIn(
                organizationId,
                List.of(
                        ContractStatus.ACTIVE,
                        ContractStatus.PENDING
                )
        );
    }

    private Contract getActiveContract(
            List<Contract> contracts
    ) {

        return contracts.stream()
                .filter(c -> c.getStatus() == ContractStatus.ACTIVE)
                .findFirst()
                .orElse(null);
    }

    private void validateNoContractOverlap(
            UUID organizationId,
            Contract targetContract,
            UUID excludedContractId
    ) {

        List<Contract> existing =
                getActiveAndPendingContracts(organizationId);

        boolean hasConflict =
                existing.stream()
                        .filter(c ->
                                excludedContractId == null
                                        || !c.getId().equals(excludedContractId)
                        )
                        .anyMatch(targetContract::overlapsWith);

        if (hasConflict) {
            throw new Exceptions(
                    "Contract overlaps with active or pending contract",
                    HttpStatus.CONFLICT
            );
        }
    }
}