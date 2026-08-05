package pe.dcs.app.features.organization;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.organization.request.OrganizationCreateRequest;
import pe.dcs.app.features.organization.request.OrganizationListRequest;
import pe.dcs.app.features.organization.request.OrganizationUpdateRequest;
import pe.dcs.app.features.organization.response.OrganizationListResponse;
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
                "success.organizacionesFiltradas",
                service.findAll(request)
        );
    }

    @GetMapping("/list")
    public ApiResponse<List<OrganizationListResponse>> list() {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.listadoOrganizaciones",
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
                "success.organizacionEncontrada",
                service.findById(id)
        );
    }

    // =====================================================
    // CREATE
    // =====================================================
    @PostMapping("/create")
    public ApiResponse<OrganizationResponse> create(
            @Valid @RequestBody OrganizationCreateRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.CREATED.value(),
                "success.organizacionCreada",
                service.create(request)
        );
    }

    // =====================================================
    // UPDATE
    // =====================================================
    @PutMapping("/update/{id}")
    public ApiResponse<OrganizationResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody OrganizationUpdateRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.organizacionActualizada",
                service.update(id, request)
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
                "success.organizacionHabilitada",
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
                "success.organizacionDeshabilitada",
                null
        );
    }
}