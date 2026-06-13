package pe.dcs.app.features.ministry_role;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Ministry;
import pe.dcs.app.entity.MinistryRole;
import pe.dcs.app.features.ministry_role.request.MinistryRoleFilter;
import pe.dcs.app.features.ministry_role.request.MinistryRoleRequest;
import pe.dcs.app.features.ministry_role.request.MinistryRoleSearchRequest;
import pe.dcs.app.features.ministry_role.mapper.MinistryRoleMapper;
import pe.dcs.app.features.ministry_role.response.MinistryRoleResponse;
import pe.dcs.app.features.ministry_role.service.MinistryRoleService;
import pe.dcs.app.repository.MinistryRepository;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;
import pe.dcs.app.repository.MinistryRoleRepository;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageableUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MinistryRoleServiceImpl implements MinistryRoleService {

    private final MinistryRoleRepository repository;
    private final MinistryRoleMapper mapper;
    private final MinistryRepository ministryRepository;

    @Override
    @Transactional
    public MinistryRoleResponse create(MinistryRoleRequest request) {

        MinistryRole role = new MinistryRole();
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        role.setActive(true);

        // ministry optional
        if (request.getMinistryId() != null) {
            Ministry ministry = ministryRepository.findById(request.getMinistryId())
                    .orElseThrow(() -> new RuntimeException("Ministry not found"));
            role.setMinistry(ministry);
        } else {
            role.setMinistry(null); // global role
        }

        // default fallback
        role.setRequiresActiveMembership(
                resolveRequiresActiveMembership(request.getRequiresActiveMembership())
        );

        return mapper.simple(repository.save(role));
    }

    @Override
    @Transactional
    public MinistryRoleResponse update(UUID id, MinistryRoleRequest request) {

        MinistryRole role = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ministry role not found"));

        role.setName(request.getName());
        role.setDescription(request.getDescription());

        // ministry update (allow null)
        if (request.getMinistryId() != null) {
            Ministry ministry = ministryRepository.findById(request.getMinistryId())
                    .orElseThrow(() -> new RuntimeException("Ministry not found"));
            role.setMinistry(ministry);
        } else {
            role.setMinistry(null);
        }

        // requiresActiveMembership update
        if (request.getRequiresActiveMembership() != null) {
            role.setRequiresActiveMembership(request.getRequiresActiveMembership());
        }

        role.setUpdatedAt(LocalDateTime.now());

        return mapper.simple(repository.save(role));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        repository.deleteById(id);
    }

    @Override
    public PageResponse<MinistryRoleResponse> search(MinistryRoleSearchRequest request) {

        Pageable pageable =
                PageableUtil.buildPageable(request.getPagination(), request.getSorts());

        MinistryRoleFilter filters = request.getFilters();

        Specification<MinistryRole> spec =
                MinistryRoleSpecification.filter(
                        filters != null ? filters.getName() : null,
                        filters != null ? filters.getActive() : null
                );

        Page<MinistryRole> page = repository.findAll(spec, pageable);

        return new PageResponse<>(
                page.getContent().stream().map(mapper::simple).toList(),
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    @Override
    public List<MinistryRoleResponse> findAll() {

        return repository.findAllByActiveTrueOrderByNameAsc()
                .stream()
                .map(mapper::simple)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MinistryRoleResponse getById(UUID id) {

        MinistryRole ministryRole =
                repository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Rol ministerial no encontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        return mapper.simple(ministryRole);
    }

    private boolean resolveRequiresActiveMembership(Boolean value) {
        return value != null ? value : true;
    }
}