package pe.dcs.app.features.ministry;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.ministry.request.MinistryRequest;
import pe.dcs.app.features.ministry.request.MinistrySearchRequest;
import pe.dcs.app.features.ministry.response.MinistryResponse;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.features.ministry.service.MinistryService;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ministries")
@RequiredArgsConstructor
public class MinistryController {

    private final MinistryService ministryService;

    @PostMapping("/create")
    public ApiResponse<MinistryResponse> create(@Valid @RequestBody MinistryRequest request) {
        return new ApiResponse<>(200, "success.ministryCreated", ministryService.create(request));
    }

    @PutMapping("/update/{id}")
    public ApiResponse<MinistryResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody MinistryRequest request
    ) {
        return new ApiResponse<>(200, "success.ministryUpdated", ministryService.update(id, request));
    }

    @PostMapping("/enable/{id}")
    public ApiResponse<Void> enable(@PathVariable UUID id) {
        ministryService.enable(id);
        return new ApiResponse<>(200, "success.ministryActive", null);
    }

    @PostMapping("/disable/{id}")
    public ApiResponse<Void> disable(@PathVariable UUID id) {
        ministryService.disable(id);
        return new ApiResponse<>(200, "success.ministryDesactive", null);
    }

    @PostMapping("/search")
    public ApiResponse<PageResponse<MinistryResponse>> search(
            @RequestBody MinistrySearchRequest request
    ) {
        return new ApiResponse<>(200, "success.ministriesFetched", ministryService.search(request));
    }

    @GetMapping("/all")
    public ApiResponse<List<MinistryResponse>> findAll() {

        return new ApiResponse<>(
                200,
                "success.ministriesFetchedSuccessfully",
                ministryService.findAll()
        );
    }

    @GetMapping("/find/{id}")
    public ApiResponse<MinistryResponse> getById(@PathVariable UUID id) {

        return new ApiResponse<>(
                200,
                "success.ministerioObtenidoCorrectamente",
                ministryService.getById(id)
        );
    }
}