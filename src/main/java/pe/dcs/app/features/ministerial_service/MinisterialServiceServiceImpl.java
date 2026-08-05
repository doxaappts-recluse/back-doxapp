package pe.dcs.app.features.ministerial_service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Person;
import pe.dcs.app.features.ministerial_service.mapper.MinisterialServiceMapper;
import pe.dcs.app.features.ministerial_service.request.MinisterialServiceSearchRequest;
import pe.dcs.app.features.ministerial_service.response.MinisterialServiceResponse;
import pe.dcs.app.features.ministerial_service.service.MinisterialServiceService;
import pe.dcs.app.repository.MinistryAssignmentRepository;
import pe.dcs.app.repository.PersonRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.enums.resolveSort.PersonSort;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

@Service
@RequiredArgsConstructor
public class MinisterialServiceServiceImpl implements MinisterialServiceService {

    private final PersonRepository personRepository;
    private final MinistryAssignmentRepository ministryAssignmentRepository;
    private final MinisterialServiceMapper mapper;
    private final AuthContext authContext;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MinisterialServiceResponse> search(MinisterialServiceSearchRequest request) {

        assertCallerCanManage();

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts(),
                        PersonSort::resolvePath
                );

        Page<Person> page =
                personRepository.findAll(
                        MinisterialServiceSpecification.filter(request, authContext),
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(person -> mapper.toSearchRow(
                                person,
                                ministryAssignmentRepository
                                        .findFirstByPersonIdAndEndDateIsNullOrderByStartDateDesc(person.getId())
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

    private void assertCallerCanManage() {
        authContext.assertCanManageCurrent("error.noTienePermisosGestionarServicioMinisterial");
    }

}
