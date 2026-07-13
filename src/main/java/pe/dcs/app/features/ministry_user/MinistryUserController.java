package pe.dcs.app.features.ministry_user;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ministry-user")
@RequiredArgsConstructor
public class MinistryUserController {

    /*private final MinistryUserSearchService service;

    @PostMapping("/search")
    public ApiResponse<PageResponse<MinistryUserSearchResponse>> search(
            @RequestBody MinistryUserSearchRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Ministry users retrieved successfully",
                service.search(request)
        );
    }*/
}