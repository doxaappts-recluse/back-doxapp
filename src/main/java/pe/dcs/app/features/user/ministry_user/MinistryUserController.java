package pe.dcs.app.features.user.ministry_user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.user.ministry_user.request.MinistryUserSearchRequest;
import pe.dcs.app.features.user.ministry_user.response.MinistryUserSearchResponse;
import pe.dcs.app.features.user.ministry_user.service.MinistryUserSearchService;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

@RestController
@RequestMapping("/api/v1/ministry-user")
@RequiredArgsConstructor
public class MinistryUserController {

    private final MinistryUserSearchService service;

    @PostMapping("/search")
    public ApiResponse<PageResponse<MinistryUserSearchResponse>> search(
            @RequestBody MinistryUserSearchRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Ministry users retrieved successfully",
                service.search(request)
        );
    }
}