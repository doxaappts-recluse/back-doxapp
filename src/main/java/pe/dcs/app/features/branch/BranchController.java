package pe.dcs.app.features.branch;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.branch.request.BranchCreateRequest;
import pe.dcs.app.features.branch.request.BranchListRequest;
import pe.dcs.app.features.branch.request.BranchUpdateRequest;
import pe.dcs.app.features.branch.response.BranchResponse;
import pe.dcs.app.features.branch.service.BranchService;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/branches")
public class BranchController {

    private final BranchService service;

    @PostMapping("/search/{organizationId}")
    public ApiResponse<PageResponse<BranchResponse>> list(
            @PathVariable UUID organizationId,
            @RequestBody BranchListRequest request
    ) {

        return new ApiResponse<>(
                200,
                "Sedes Obtenidas",
                service.findByOrganization(
                    organizationId,
                    request
                )
        );
    }

    // =====================================================
    // CREAR SEDE
    // =====================================================

    @PostMapping("/create/{organizationId}")
    public ApiResponse<BranchResponse> create(
            @PathVariable UUID organizationId,
            @RequestBody @Valid BranchCreateRequest request
    ) {

        return new ApiResponse<>(
                200,
                "Sede creada",
                service.create(
                        organizationId,
                        request
                )
        );
    }

    // =====================================================
    // EDITAR SEDE
    // =====================================================

    @PutMapping("/update/{id}")
    public ApiResponse<BranchResponse> update(
            @PathVariable UUID id,
            @RequestBody @Valid BranchUpdateRequest request
    ) {
        return new ApiResponse<>(
                200,
                "Sede creada",
                service.update(
                        id,
                        request
                )
        );
    }


    // =====================================================
    // HABILITAR SEDE
    // =====================================================

    @PatchMapping("/enable/{id}")
    public ApiResponse<Void> enable(
            @PathVariable UUID id
    ) {

        service.enable(id);
        return new ApiResponse<>(
                200,
                "Sede habilitada",
                null
        );

    }


    // =====================================================
    // DESHABILITAR SEDE
    // =====================================================

    @PatchMapping("/disable/{id}")
    public ApiResponse<Void> disable(
            @PathVariable UUID id
    ) {

        service.disable(id);
        return new ApiResponse<>(
                200,
                "Sede deshabilitada",
                null
        );

    }


    // =====================================================
    // CAMBIAR SEDE PRINCIPAL
    // =====================================================

    @PatchMapping("/main/{id}")
    public ApiResponse<Void> changeMain(
            @PathVariable UUID id
    ) {

        service.changeMain(id);

        return new ApiResponse<>(
                200,
                "Sede principal cambiada",
                null
        );
    }

}