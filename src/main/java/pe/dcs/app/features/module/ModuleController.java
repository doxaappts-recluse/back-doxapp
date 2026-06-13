package pe.dcs.app.features.module;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.module.request.ModuleRequest;
import pe.dcs.app.features.module.request.ModuleSearchRequest;
import pe.dcs.app.features.module.response.ModuleOptionResponse;
import pe.dcs.app.features.module.response.ModuleResponse;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.features.module.service.ModuleService;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/modules")
public class ModuleController {

    private final ModuleService moduleService;

    public ModuleController(ModuleService moduleService) {
        this.moduleService = moduleService;
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<ModuleResponse>>> search(
            @RequestBody ModuleSearchRequest request
    ) {

        return ResponseEntity.ok(
                new ApiResponse<>(200, "Módulos filtrados", moduleService.search(request)
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
                        "Módulo encontrado",
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
                        "Módulos padres filtrados",
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
                "Módulos hijos",
                moduleService.getChildModules(currentId)
        );
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<ModuleResponse>> create(@RequestBody ModuleRequest req) {
        return ResponseEntity.ok(
                new ApiResponse<>(200, "Módulo creado", moduleService.create(req))
        );
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<ApiResponse<ModuleResponse>> update(
            @PathVariable UUID id,
            @RequestBody ModuleRequest req
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(200, "Módulo actualizado", moduleService.update(id, req))
        );
    }

    @PatchMapping("/{id}/enable")
    public ResponseEntity<ApiResponse<ModuleResponse>> enable(
            @PathVariable UUID id
    ) {

        return ResponseEntity.ok(
                new ApiResponse<>(200, "Módulo habilitado", moduleService.enable(id))
        );
    }

    @PatchMapping("/{id}/disable")
    public ResponseEntity<?> disable(@PathVariable UUID id) {
        return ResponseEntity.ok(
                new ApiResponse<>(200, "Módulo deshabilitado", moduleService.disable(id))
        );
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        return ResponseEntity.ok(
                new ApiResponse<>(200, "Módulo eliminado", moduleService.delete(id))
        );
    }
}