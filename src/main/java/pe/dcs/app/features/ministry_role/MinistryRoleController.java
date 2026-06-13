package pe.dcs.app.features.ministry_role;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.ministry_role.request.MinistryRoleRequest;
import pe.dcs.app.features.ministry_role.request.MinistryRoleSearchRequest;
import pe.dcs.app.features.ministry_role.response.MinistryRoleResponse;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.features.ministry_role.service.MinistryRoleService;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ministry-roles")
@RequiredArgsConstructor
public class MinistryRoleController {

    private final MinistryRoleService service;

    @PostMapping("/create")
    public ApiResponse<MinistryRoleResponse> create(@RequestBody MinistryRoleRequest request) {
        return new ApiResponse<>(200, "Ministry role created", service.create(request));
    }

    @PutMapping("/update/{id}")
    public ApiResponse<MinistryRoleResponse> update(
            @PathVariable UUID id,
            @RequestBody MinistryRoleRequest request
    ) {
        return new ApiResponse<>(200, "Ministry role updated", service.update(id, request));
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return new ApiResponse<>(200, "Ministry role deleted", null);
    }

    @PostMapping("/search")
    public ApiResponse<PageResponse<MinistryRoleResponse>> search(
            @RequestBody MinistryRoleSearchRequest request
    ) {
        return new ApiResponse<>(200, "Ministry roles fetched", service.search(request));
    }

    @GetMapping("/all")
    public ApiResponse<List<MinistryRoleResponse>> findAll() {

        return new ApiResponse<>(
                200,
                "Ministry roles fetched successfully",
                service.findAll()
        );
    }

    @GetMapping("/find/{id}")
    public ApiResponse<MinistryRoleResponse> getById(@PathVariable UUID id) {

        return new ApiResponse<>(
                200,
                "Rol ministerial obtenido correctamente",
                service.getById(id)
        );
    }

}
