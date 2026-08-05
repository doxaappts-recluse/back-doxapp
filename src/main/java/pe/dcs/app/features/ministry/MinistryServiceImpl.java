package pe.dcs.app.features.ministry;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Ministry;
import pe.dcs.app.repository.MinistryRepository;
import pe.dcs.app.features.ministry.mapper.MinistryMapper;
import pe.dcs.app.features.ministry.request.MinistryFilter;
import pe.dcs.app.features.ministry.request.MinistryRequest;
import pe.dcs.app.features.ministry.request.MinistrySearchRequest;
import pe.dcs.app.features.ministry.response.MinistryResponse;
import pe.dcs.app.features.ministry.service.MinistryService;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageableUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MinistryServiceImpl implements MinistryService {

    private final MinistryRepository ministryRepository;
    private final MinistryMapper ministryMapper;
    private final AuthContext authContext;

    /**
     * El catálogo de ministerios es de gestión exclusiva de
     * SYSTEM. El listado que consumen org/branch admin para
     * asignar servicio ministerial se resuelve aparte, en
     * MinistryAssignmentService (getAllMinistries), que no pasa
     * por este service.
     */
    private void assertSystem() {

        if (!authContext.isSystem()) {

            throw new Exceptions(
                    "error.soloAdministradorSistemaPuedeGestionarCatalogo",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    @Override
    @Transactional
    public MinistryResponse create(MinistryRequest request){

        assertSystem();

        String code = request.getCode().trim().toUpperCase();

        if (ministryRepository.existsByCodeIgnoreCase(code)) {
            throw new Exceptions(
                    "error.ministryCodeAlreadyExists",
                    HttpStatus.BAD_REQUEST
            );
        }

        Ministry ministry = new Ministry();

        ministry.setCode(code);
        ministry.setNameEs(request.getNameEs());
        ministry.setNameEn(request.getNameEn());
        ministry.setDescription(request.getDescription());

        ministry.setStatus(
                request.getStatus() != null
                        ? request.getStatus()
                        : StatusType.ACTIVE
        );

        return ministryMapper.simple(ministryRepository.save(ministry), authContext.canViewAudit());

    }

    @Override
    @Transactional
    public MinistryResponse update(UUID id, MinistryRequest request){

        assertSystem();

        Ministry ministry =
                ministryRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.ministerioNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (request.getCode() != null
                && ministryRepository.existsByCodeIgnoreCaseAndIdNot(request.getCode().trim().toUpperCase(), id)) {

            throw new Exceptions(
                    "error.ministryCodeAlreadyExists",
                    HttpStatus.BAD_REQUEST
            );
        }

        ministryMapper.updateEntity(ministry, request);

        return ministryMapper.simple(ministryRepository.save(ministry), authContext.canViewAudit());

    }

    @Override
    @Transactional
    public void disable(UUID id){

        assertSystem();

        Ministry ministry =
                ministryRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.ministerioNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        ministry.setStatus(StatusType.INACTIVE);

        ministryRepository.save(ministry);

    }
    @Override
    @Transactional
    public void enable(UUID id){

        assertSystem();

        Ministry ministry =
                ministryRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.ministerioNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        ministry.setStatus(StatusType.ACTIVE);

        ministryRepository.save(ministry);

    }

    @Override
    @Transactional(readOnly = true)
    public MinistryResponse getById(UUID id){

        assertSystem();

        Ministry ministry =
                ministryRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.ministerioNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        return ministryMapper.simple(ministry, authContext.canViewAudit());

    }

    @Override
    @Transactional(readOnly = true)
    public List<MinistryResponse> findAll(){

        assertSystem();

        boolean showAudit = authContext.canViewAudit();

        return ministryRepository
                .findAllByStatusOrderByNameEsAsc(
                        StatusType.ACTIVE
                )
                .stream()
                .map(ministry -> ministryMapper.simple(ministry, showAudit))
                .toList();

    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MinistryResponse> search(MinistrySearchRequest request){

        assertSystem();

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        MinistryFilter filters = request.getFilters();

        Specification<Ministry> spec =
                MinistrySpecification.filter(
                        filters != null
                                ? filters.getName()
                                : null,

                        filters != null
                                ? filters.getStatus()
                                : null
                );

        Page<Ministry> page =
                ministryRepository.findAll(
                        spec,
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(ministry -> ministryMapper.simple(ministry, showAudit))
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