package pe.dcs.app.features.user.ministry_user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import pe.dcs.app.entity.User;
import pe.dcs.app.repository.UserRepository;
import pe.dcs.app.features.user.ministry_user.mapper.MinistryUserMapper;
import pe.dcs.app.features.user.ministry_user.request.MinistryUserSearchRequest;
import pe.dcs.app.features.user.ministry_user.response.MinistryUserSearchResponse;
import pe.dcs.app.features.user.ministry_user.service.MinistryUserSearchService;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MinistryUserSearchServiceImpl implements MinistryUserSearchService {

    private final UserRepository userRepository;
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
    }
}