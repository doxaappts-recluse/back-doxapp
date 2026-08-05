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
import pe.dcs.app.features.ministry_role.mapper.MinistryRoleMapper;
import pe.dcs.app.features.ministry_role.request.MinistryRoleSearchRequest;
import pe.dcs.app.features.ministry_role.response.MinistryRoleResponse;
import pe.dcs.app.features.ministry_role.service.MinistryRoleService;
import pe.dcs.app.repository.MinistryRepository;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.repository.MinistryRoleRepository;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

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
    public MinistryRoleResponse create(MinistryRoleRequest request){

        Ministry ministry =
                ministryRepository.findById(request.getMinistryId())
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.ministerioNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        String code = request.getCode().trim().toUpperCase();

        if (repository.existsByMinistryIdAndCodeIgnoreCase(ministry.getId(), code)) {
            throw new Exceptions(
                    "error.ministryRoleCodeAlreadyExists",
                    HttpStatus.BAD_REQUEST
            );
        }

        MinistryRole role = new MinistryRole();

        role.setCode(code);
        role.setNameEs(request.getNameEs());
        role.setNameEn(request.getNameEn());
        role.setDescription(request.getDescription());
        role.setMinistry(ministry);
        role.setStatus(StatusType.ACTIVE);

        role.setRequiresActiveMembership(
                request.getRequiresActiveMembership() != null
                        ? request.getRequiresActiveMembership()
                        : true
        );

        return mapper.simple(repository.save(role));

    }

    @Override
    @Transactional
    public MinistryRoleResponse update(UUID id, MinistryRoleRequest request){

        MinistryRole role =
                repository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.rolMinisterialNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        UUID targetMinistryId =
                request.getMinistryId() != null
                        ? request.getMinistryId()
                        : role.getMinistry().getId();

        if (request.getCode() != null
                && repository.existsByMinistryIdAndCodeIgnoreCaseAndIdNot(
                        targetMinistryId,
                        request.getCode().trim().toUpperCase(),
                        id
                )) {

            throw new Exceptions(
                    "error.ministryRoleCodeAlreadyExists",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (request.getCode() != null) {
            role.setCode(request.getCode().trim().toUpperCase());
        }

        if (request.getNameEs() != null) {
            role.setNameEs(request.getNameEs());
        }

        if (request.getNameEn() != null) {
            role.setNameEn(request.getNameEn());
        }

        role.setDescription(request.getDescription());

        if(request.getMinistryId()!=null){

            Ministry ministry =
                    ministryRepository.findById(
                                    request.getMinistryId()
                            )
                            .orElseThrow(() ->
                                    new Exceptions(
                                            "error.ministerioNoEncontrado",
                                            HttpStatus.NOT_FOUND
                                    )
                            );

            role.setMinistry(ministry);
        }

        if(request.getRequiresActiveMembership()!=null){
            role.setRequiresActiveMembership(request.getRequiresActiveMembership());
        }

        return mapper.simple(repository.save(role));

    }

    @Override
    @Transactional
    public void enable(UUID id){

        MinistryRole role =
                repository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.rolMinisterialNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        role.setStatus(StatusType.ACTIVE);

        repository.save(role);

    }

    @Override
    @Transactional
    public void disable(UUID id){

        MinistryRole role =
                repository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.rolMinisterialNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        role.setStatus(StatusType.INACTIVE);

        repository.save(role);

    }

    @Override
    @Transactional(readOnly = true)
    public List<MinistryRoleResponse> findAll(){

        return repository
                .findAllByStatusOrderByNameEsAsc(
                        StatusType.ACTIVE
                )
                .stream()
                .map(mapper::simple)
                .toList();

    }

    @Override
    @Transactional(readOnly = true)
    public MinistryRoleResponse getById(UUID id) {

        MinistryRole role =
                repository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.rolMinisterialNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        return mapper.simple(role);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MinistryRoleResponse> search(UUID id, MinistryRoleSearchRequest request) {

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        MinistryRoleFilter filters = request.getFilters();

        Specification<MinistryRole> spec =
                MinistryRoleSpecification.filter(
                        id,
                        filters != null ? filters.getName() : null,
                        filters != null ? filters.getActive() : null
                );


        Page<MinistryRole> page =
                repository.findAll(
                        spec,
                        pageable
                );


        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(mapper::simple)
                        .toList(),

                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

}