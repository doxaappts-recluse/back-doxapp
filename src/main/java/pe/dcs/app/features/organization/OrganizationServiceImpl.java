package pe.dcs.app.features.organization;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.Organization;
import pe.dcs.app.features.organization.mapper.OrganizationMapper;
import pe.dcs.app.features.organization.request.OrganizationCreateRequest;
import pe.dcs.app.features.organization.request.OrganizationListRequest;
import pe.dcs.app.features.organization.request.OrganizationUpdateRequest;
import pe.dcs.app.features.organization.response.OrganizationListResponse;
import pe.dcs.app.features.organization.response.OrganizationResponse;
import pe.dcs.app.features.organization.service.OrganizationService;
import pe.dcs.app.repository.ContractRepository;
import pe.dcs.app.repository.OrganizationRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.DateUtils;
import pe.dcs.app.util.enums.contract.ContractStatus;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.enums.StatusType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationRepository repository;
    private final ContractRepository contractRepository;
    private final AuthContext authContext;

    /**
     * El catálogo completo de organizaciones (búsqueda/CRUD) es
     * de gestión exclusiva de SYSTEM. El único punto compartido
     * con org/branch admin es list() (dropdown simple), que en
     * cambio se acota a la organización del contexto.
     */
    private void assertSystem() {

        if (!authContext.isSystem()) {

            throw new Exceptions(
                    "error.soloAdministradorSistemaPuedeGestionarOrganizaciones",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    @Override public PageResponse<OrganizationResponse> findAll(OrganizationListRequest request) {

        assertSystem();

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        boolean showAudit = authContext.canViewAudit();

        var page = repository.findAll(
                        OrganizationSpecification.filter(request),
                        pageable
                )
                .map(organization -> OrganizationMapper.toResponse(organization, showAudit));

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

    /**
     * Dropdown simple de organizaciones (id + nombre). A
     * diferencia del resto del service, no es exclusivo de
     * SYSTEM: lo usan pantallas de org/branch admin (Accesos,
     * Org-Admin-Branch) para poblar filtros. SYSTEM ve todas
     * las organizaciones activas; org/branch admin solo ve la
     * organización de su contexto actual.
     */
    @Override
    public List<OrganizationListResponse> list() {

        List<Organization> organizations;

        if (authContext.isSystem()) {

            organizations = repository.findByStatusOrderByNameAsc(StatusType.ACTIVE);

        } else {

            UUID currentOrgId = authContext.getCurrentOrganizationId();

            organizations =
                    currentOrgId == null
                            ? List.of()
                            : repository.findById(currentOrgId)
                                    .filter(o -> o.getStatus() == StatusType.ACTIVE)
                                    .map(List::of)
                                    .orElse(List.of());
        }

        return organizations
                .stream()
                .map(
                        organization ->
                                OrganizationListResponse
                                        .builder()
                                        .id(
                                                organization.getId()
                                        )
                                        .name(
                                                organization.getName()
                                        )
                                        .build()
                )
                .toList();
    }

    @Override
    public OrganizationResponse findById(UUID id) {

        assertSystem();

        return OrganizationMapper.toResponse(getOrganization(id), authContext.canViewAudit());
    }

    @Transactional
    @Override
    public OrganizationResponse create(OrganizationCreateRequest request) {

        assertSystem();

        validateRucForCreate(request.getRuc());

        Organization organization = new Organization();

        organization.setName(request.getName());
        organization.setAddress(request.getAddress());
        organization.setRuc(request.getRuc());
        organization.setEmail(request.getEmail());
        organization.setStatus(StatusType.ACTIVE);
        organization.setFoundedDate(request.getFoundedDate());

        Branch branch = new Branch();

        branch.setName(request.getName() + " Principal");
        branch.setCode("MAIN");
        branch.setMain(true);
        branch.setOpeningDate(request.getFoundedDate());
        branch.setStatus(StatusType.ACTIVE);
        branch.setOrganization(organization);
        organization.getBranches().add(branch);

        repository.save(organization);

        return OrganizationMapper.toResponse(organization, authContext.canViewAudit());
    }

    @Override
    public OrganizationResponse update(UUID id, OrganizationUpdateRequest request) {

        assertSystem();

        Organization organization = getOrganization(id);

        validateRucForUpdate(request.getRuc(), id);

        organization.setName(request.getName());
        organization.setAddress(request.getAddress());
        organization.setRuc(request.getRuc());
        organization.setEmail(request.getEmail());
        organization.setFoundedDate(request.getFoundedDate());

        repository.save(organization);

        return OrganizationMapper.toResponse(organization, authContext.canViewAudit());
    }

    @Override
    @Transactional
    public void enable(UUID id) {

        assertSystem();

        Organization organization = getOrganization(id);

        organization.enable();

        organization.getBranches()
                .forEach(Branch::enable);

        repository.save(organization);
    }

    @Override
    @Transactional
    public void disable(UUID id) {

        assertSystem();

        Organization organization = getOrganization(id);

        boolean hasActiveContracts =
                contractRepository.existsByBranchOrganizationIdAndStatusAndEndDateGreaterThanEqual(
                        id,
                        ContractStatus.ACTIVE,
                        DateUtils.utcToday()
                );

        organization.disable(hasActiveContracts);
        organization.getBranches().forEach(Branch::disable);

        repository.save(organization);
    }

    private Organization getOrganization(UUID id) {
        return repository.findById(id)
                .orElseThrow(() ->
                        new Exceptions("error.organizationNotFound", HttpStatus.NOT_FOUND));
    }

    // =========================================
    // VALIDATIONS
    // =========================================

    private void validateRucForCreate(
            String ruc
    ) {

        boolean exists = repository.existsByRuc(ruc);

        if (exists) {
            throw new Exceptions(
                    "error.existeOrganizacionRucIngresado",
                    HttpStatus.CONFLICT
            );
        }
    }

    private void validateRucForUpdate(
            String ruc,
            UUID id
    ) {

        boolean exists =
                repository.existsByRucAndIdNot(
                        ruc,
                        id
                );

        if (exists) {
            throw new Exceptions(
                    "error.existeOrganizacionRucIngresado",
                    HttpStatus.CONFLICT
            );
        }
    }
}