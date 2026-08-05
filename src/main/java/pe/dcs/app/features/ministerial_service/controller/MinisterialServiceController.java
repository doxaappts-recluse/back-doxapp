package pe.dcs.app.features.ministerial_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.ministerial_service.request.MinisterialServiceSearchRequest;
import pe.dcs.app.features.ministerial_service.response.MinisterialServiceResponse;
import pe.dcs.app.features.ministerial_service.service.MinisterialServiceService;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

@RestController
@RequestMapping("/api/v1/ministry-user")
@RequiredArgsConstructor
public class MinisterialServiceController {

    private final MinisterialServiceService service;

    @PostMapping("/search")
    public ApiResponse<PageResponse<MinisterialServiceResponse>> search(
            @RequestBody MinisterialServiceSearchRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.personasObtenidasCorrectamente",
                service.search(request)
        );
    }

}
