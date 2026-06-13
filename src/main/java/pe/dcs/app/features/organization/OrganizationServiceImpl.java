package pe.dcs.app.features.organization;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import pe.dcs.app.entity.Organization;
import pe.dcs.app.features.organization.mapper.OrganizationMapper;
import pe.dcs.app.features.organization.request.OrganizationCreateRequest;
import pe.dcs.app.features.organization.request.OrganizationListRequest;
import pe.dcs.app.features.organization.request.OrganizationUpdateRequest;
import pe.dcs.app.features.organization.response.OrganizationResponse;
import pe.dcs.app.features.organization.service.OrganizationService;
import pe.dcs.app.repository.OrganizationRepository;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationRepository repository;

    @Override public PageResponse<OrganizationResponse> findAll(OrganizationListRequest request) {

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        var page = repository.findAll(
                        OrganizationSpecification.filter(request),
                        pageable
                )
                .map(OrganizationMapper::toResponse);

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
    public List<OrganizationResponse> list() {

        return repository.findAll()
                .stream()
                .map(OrganizationMapper::toResponse)
                .toList();
    }

    @Override
    public OrganizationResponse findById(UUID id) {

        return OrganizationMapper.toResponse(getOrganization(id));
    }

    @Override
    public OrganizationResponse create(OrganizationCreateRequest request) {

        validateRucForCreate(request.getRuc());

        Organization organization = new Organization();

        organization.setName(request.getName());
        organization.setAddress(request.getAddress());
        organization.setRuc(request.getRuc());
        organization.setStatus(StatusType.ACTIVE);

        repository.save(organization);

        return OrganizationMapper.toResponse(organization);
    }

    @Override
    public OrganizationResponse update(UUID id, OrganizationUpdateRequest request) {

        Organization organization = getOrganization(id);

        validateRucForUpdate(request.getRuc(), id);

        organization.setName(request.getName());
        organization.setAddress(request.getAddress());
        organization.setRuc(request.getRuc());

        repository.save(organization);

        return OrganizationMapper.toResponse(organization);
    }

    @Override
    public void delete(UUID id) {
        repository.delete(getOrganization(id));
    }

    @Override
    public void enable(UUID id) {

        Organization organization =  getOrganization(id);

        organization.setStatus(StatusType.ACTIVE);

        repository.save(organization);
    }

    @Override
    public void disable(UUID id) {

        Organization organization = getOrganization(id);

        organization.setStatus(StatusType.INACTIVE);

        repository.save(organization);
    }

    private Organization getOrganization(UUID id) {

        return repository.findById(id)
                .orElseThrow(() ->
                        new Exceptions("Organization not found", HttpStatus.NOT_FOUND));
    }

    // =========================================
// VALIDATIONS
// =========================================

    private void validateRucForCreate(
            String ruc
    ) {

        boolean exists =
                repository.existsByRuc(ruc);

        if (exists) {
            throw new Exceptions(
                    "Ya existe una organización con el RUC ingresado",
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
                    "Ya existe una organización con el RUC ingresado",
                    HttpStatus.CONFLICT
            );
        }
    }
}