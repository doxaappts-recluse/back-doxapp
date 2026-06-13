package pe.dcs.app.features.ministry;

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
    public ApiResponse<MinistryResponse> create(@RequestBody MinistryRequest request) {
        return new ApiResponse<>(200, "Ministry created", ministryService.create(request));
    }

    @PutMapping("/update/{id}")
    public ApiResponse<MinistryResponse> update(
            @PathVariable UUID id,
            @RequestBody MinistryRequest request
    ) {
        return new ApiResponse<>(200, "Ministry updated", ministryService.update(id, request));
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        ministryService.delete(id);
        return new ApiResponse<>(200, "Ministry deleted", null);
    }

    @PostMapping("/search")
    public ApiResponse<PageResponse<MinistryResponse>> search(
            @RequestBody MinistrySearchRequest request
    ) {
        return new ApiResponse<>(200, "Ministries fetched", ministryService.search(request));
    }

    @GetMapping("/all")
    public ApiResponse<List<MinistryResponse>> findAll() {

        return new ApiResponse<>(
                200,
                "Ministries fetched successfully",
                ministryService.findAll()
        );
    }

    @GetMapping("/find/{id}")
    public ApiResponse<MinistryResponse> getById(@PathVariable UUID id) {

        return new ApiResponse<>(
                200,
                "Ministerio obtenido correctamente",
                ministryService.getById(id)
        );
    }
}