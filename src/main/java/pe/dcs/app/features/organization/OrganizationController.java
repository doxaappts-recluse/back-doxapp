package pe.dcs.app.features.organization;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.organization.request.OrganizationCreateRequest;
import pe.dcs.app.features.organization.request.OrganizationListRequest;
import pe.dcs.app.features.organization.request.OrganizationUpdateRequest;
import pe.dcs.app.features.organization.response.OrganizationResponse;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.features.organization.service.OrganizationService;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService service;

    // =====================================================
    // LIST
    // =====================================================
    @PostMapping("/search")
    public ApiResponse<PageResponse<OrganizationResponse>> search(
            @RequestBody OrganizationListRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Organizaciones filtradas",
                service.findAll(request)
        );
    }

    @GetMapping("/list")
    public ApiResponse<List<OrganizationResponse>> list() {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Listado de organizaciones",
                service.list()
        );
    }

    // =====================================================
    // GET BY ID
    // =====================================================
    @GetMapping("/get/{id}")
    public ApiResponse<OrganizationResponse> findById(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Organización encontrada",
                service.findById(id)
        );
    }

    // =====================================================
    // CREATE
    // =====================================================
    @PostMapping("/create")
    public ApiResponse<OrganizationResponse> create(
            @RequestBody OrganizationCreateRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.CREATED.value(),
                "Organización creada",
                service.create(request)
        );
    }

    // =====================================================
    // UPDATE
    // =====================================================
    @PutMapping("/update/{id}")
    public ApiResponse<OrganizationResponse> update(
            @PathVariable UUID id,
            @RequestBody OrganizationUpdateRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Organización actualizada",
                service.update(id, request)
        );
    }

    // =====================================================
    // DELETE
    // =====================================================
    @DeleteMapping("/delete/{id}")
    public ApiResponse<Void> delete(
            @PathVariable UUID id
    ) {

        service.delete(id);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Organización eliminada",
                null
        );
    }

    // =====================================================
    // ENABLE
    // =====================================================
    @PatchMapping("/{id}/enable")
    public ApiResponse<Void> enable(
            @PathVariable UUID id
    ) {

        service.enable(id);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Organización habilitada",
                null
        );
    }

    // =====================================================
    // DISABLE
    // =====================================================
    @PatchMapping("/{id}/disable")
    public ApiResponse<Void> disable(
            @PathVariable UUID id
    ) {

        service.disable(id);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Organización deshabilitada",
                null
        );
    }
}