package pe.dcs.app.features.ministry_user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.dcs.app.features.ministry_user.service.MinistryUserSearchService;

@Service
@RequiredArgsConstructor
public class MinistryUserSearchServiceImpl implements MinistryUserSearchService {

    /*private final UserRepository userRepository;
    private final AuthContext authContext;

    public PageResponse<MinistryUserSearchResponse> search(
            MinistryUserSearchRequest request
    ) {

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        UUID orgId = authContext.getOrganizationId();

        var filters = request.getFilters();

        Page<User> page =
                userRepository.findAll(
                        MinistryUserSpecification.filter(
                                orgId,
                                filters != null ? filters.getName() : null,
                                filters != null ? filters.getLastname() : null,
                                filters != null ? filters.getHasMinistry() : null
                        ),
                        pageable
                );

        List<MinistryUserSearchResponse> content =
                page.getContent()
                        .stream()
                        .map(MinistryUserMapper::map)
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
    }*/
}