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
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageableUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MinistryServiceImpl implements MinistryService {

    private final MinistryRepository ministryRepository;
    private final MinistryMapper ministryMapper;

    @Override
    @Transactional
    public MinistryResponse create(MinistryRequest request) {

        Ministry ministry = new Ministry();
        ministry.setName(request.getName());
        ministry.setDescription(request.getDescription());
        ministry.setActive(true);
        ministry.setCreatedAt(LocalDateTime.now());

        return ministryMapper.simple(ministryRepository.save(ministry));
    }

    @Override
    @Transactional
    public MinistryResponse update(UUID id, MinistryRequest request) {

        Ministry ministry = ministryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ministry not found"));

        ministry.setName(request.getName());
        ministry.setDescription(request.getDescription());
        ministry.setUpdatedAt(LocalDateTime.now());

        return ministryMapper.simple(ministryRepository.save(ministry));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        ministryRepository.deleteById(id);
    }

    @Override
    public PageResponse<MinistryResponse> search(MinistrySearchRequest request) {

        Pageable pageable =
                PageableUtil.buildPageable(request.getPagination(), request.getSorts());

        MinistryFilter filters = request.getFilters();

        Specification<Ministry> spec =
                MinistrySpecification.filter(
                        filters != null ? filters.getName() : null,
                        filters != null ? filters.getActive() : null
                );

        Page<Ministry> page = ministryRepository.findAll(spec, pageable);

        return new PageResponse<>(
                page.getContent().stream().map(ministryMapper::simple).toList(),
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public MinistryResponse getById(UUID id) {

        Ministry ministry =
                ministryRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Ministerio no encontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        return ministryMapper.simple(ministry);
    }

    @Override
    public List<MinistryResponse> findAll() {

        return ministryRepository.findAllByActiveTrueOrderByNameAsc()
                .stream()
                .map(ministryMapper::simple)
                .toList();
    }
}