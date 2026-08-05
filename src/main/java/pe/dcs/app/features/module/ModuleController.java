package pe.dcs.app.features.module;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.module.request.ModuleRequest;
import pe.dcs.app.features.module.request.ModuleSearchRequest;
import pe.dcs.app.features.module.response.ContractModuleAccessResponse;
import pe.dcs.app.features.module.response.ModuleOptionResponse;
import pe.dcs.app.features.module.response.ModuleResponse;
import pe.dcs.app.features.module.service.ContractModuleAccessService;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.features.module.service.ModuleService;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/modules")
public class ModuleController {

    private final ModuleService moduleService;
    private final ContractModuleAccessService contractModuleAccessService;

    public ModuleController(
            ModuleService moduleService,
            ContractModuleAccessService contractModuleAccessService
    ) {
        this.moduleService = moduleService;
        this.contractModuleAccessService = contractModuleAccessService;
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<ModuleResponse>>> search(
            @RequestBody ModuleSearchRequest request
    ) {

        return ResponseEntity.ok(
                new ApiResponse<>(200, "success.modulosFiltrados", moduleService.search(request)
                )
        );
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<ApiResponse<ModuleResponse>> getById(
            @PathVariable UUID id
    ) {

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "success.moduloEncontrado",
                        moduleService.getById(id)
                )
        );
    }

    @GetMapping("/parents")
    public ResponseEntity<ApiResponse<List<ModuleOptionResponse>>> getParents(
            @RequestParam(required = false) UUID currentId
    ) {

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "success.modulosPadresFiltrados",
                        moduleService.getParentModules(currentId)
                )
        );
    }

    @GetMapping("/children")
    public ApiResponse<List<ModuleOptionResponse>> getChildren(
            @RequestParam(required = false) UUID currentId
    ) {

        return new ApiResponse<>(
                200,
                "success.modulosHijos",
                moduleService.getChildModules(currentId)
        );
    }

    /**
     * Módulos hijos + permisos habilitados por el contrato
     * ACTIVO de la sede (o de la sede en contexto, para
     * ORG_BRANCH_ADMIN). Ya viene filtrado: listo para
     * pintar el formulario de asignación de accesos.
     */
    @GetMapping("/contract-access")
    public ApiResponse<List<ContractModuleAccessResponse>> getContractAccess(
            @RequestParam(required = false) UUID branchId
    ) {

        return new ApiResponse<>(
                200,
                "success.modulosDisponiblesContratoActivo",
                contractModuleAccessService.getAvailableModules(branchId)
        );
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<ModuleResponse>> create(@Valid @RequestBody ModuleRequest req) {
        return ResponseEntity.ok(
                new ApiResponse<>(200, "success.moduloCreado", moduleService.create(req))
        );
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<ApiResponse<ModuleResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody ModuleRequest req
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(200, "success.moduloActualizado", moduleService.update(id, req))
        );
    }

    @PatchMapping("/{id}/enable")
    public ResponseEntity<ApiResponse<ModuleResponse>> enable(
            @PathVariable UUID id
    ) {

        return ResponseEntity.ok(
                new ApiResponse<>(200, "success.moduloHabilitado", moduleService.enable(id))
        );
    }

    @PatchMapping("/{id}/disable")
    public ResponseEntity<?> disable(@PathVariable UUID id) {
        return ResponseEntity.ok(
                new ApiResponse<>(200, "success.moduloDeshabilitado", moduleService.disable(id))
        );
    }

}