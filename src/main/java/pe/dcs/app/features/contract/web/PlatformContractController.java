package pe.dcs.app.features.contract.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.contract.domain.RenewalType;
import pe.dcs.app.features.contract.dto.BranchOption;
import pe.dcs.app.features.contract.dto.ContractActionRequest;
import pe.dcs.app.features.contract.dto.ContractRequest;
import pe.dcs.app.features.contract.dto.ContractResponse;
import pe.dcs.app.features.contract.dto.ContractSearchRequest;
import pe.dcs.app.features.contract.dto.MaintenanceResponse;
import pe.dcs.app.features.contract.dto.NextStartResponse;
import pe.dcs.app.features.contract.service.ContractLifecycleService;
import pe.dcs.app.features.contract.service.ContractService;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

/**
 * M03 · Contratos (N1). SYSTEM_ADMIN: V C E S · SYSTEM_SUPPORT: solo V. Editar, corregir, renovar, mejorar y reducir
 * son edición (E); activar, suspender y cancelar son cambio de estado (S).
 */
@RestController
@RequestMapping("/api/v1/platform/contracts")
@RequiredArgsConstructor
public class PlatformContractController {

    private static final String MODULE = "CONTRACT";

    private final ContractService service;
    private final ContractLifecycleService lifecycle;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<ContractResponse>> search(@RequestBody(required = false) ContractSearchRequest req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(req));
    }

    /** Sedes de una organización (para elegir alcance y reparto de licencias). */
    @GetMapping("/branches/{organizationId}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<BranchOption>> branches(@PathVariable UUID organizationId) {
        return new ApiResponse<>(200, "ok.common.sent", service.branchesOf(organizationId));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<ContractResponse> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(id));
    }

    /** Inicio del nuevo periodo para renew | upgrade | downgrade (única fórmula de fechas). */
    @GetMapping("/{id}/next-start")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<NextStartResponse> nextStart(@PathVariable UUID id, @RequestParam String type) {
        return new ApiResponse<>(200, "ok.common.sent", service.nextStart(id, type));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<ContractResponse> create(@Valid @RequestBody ContractRequest req) {
        return new ApiResponse<>(201, "ok.contract.created", service.create(req));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<ContractResponse> update(@PathVariable UUID id, @Valid @RequestBody ContractRequest req) {
        return new ApiResponse<>(200, "ok.contract.updated", service.update(id, req));
    }

    @PostMapping("/{id}/correct")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<ContractResponse> correct(@PathVariable UUID id, @Valid @RequestBody ContractRequest req) {
        return new ApiResponse<>(200, "ok.contract.corrected", service.correct(id, req));
    }

    @PostMapping("/{id}/renew")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<ContractResponse> renew(@PathVariable UUID id, @Valid @RequestBody ContractRequest req) {
        return new ApiResponse<>(200, "ok.contract.renewed", service.transition(id, RenewalType.RENEWAL, req));
    }

    @PostMapping("/{id}/upgrade")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<ContractResponse> upgrade(@PathVariable UUID id, @Valid @RequestBody ContractRequest req) {
        return new ApiResponse<>(200, "ok.contract.upgraded", service.transition(id, RenewalType.UPGRADE, req));
    }

    @PostMapping("/{id}/downgrade")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<ContractResponse> downgrade(@PathVariable UUID id, @Valid @RequestBody ContractRequest req) {
        return new ApiResponse<>(200, "ok.contract.downgraded", service.transition(id, RenewalType.DOWNGRADE, req));
    }

    /** PENDING → ACTIVE y SUSPENDED → ACTIVE. */
    @PostMapping("/{id}/activate")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<ContractResponse> activate(@PathVariable UUID id, @RequestBody(required = false) ContractActionRequest req) {
        return new ApiResponse<>(200, "ok.contract.activated", service.activate(id, req));
    }

    @PostMapping("/{id}/suspend")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<ContractResponse> suspend(@PathVariable UUID id, @Valid @RequestBody ContractActionRequest req) {
        return new ApiResponse<>(200, "ok.contract.suspended", service.suspend(id, req));
    }

    @PostMapping("/{id}/cancel")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<ContractResponse> cancel(@PathVariable UUID id, @Valid @RequestBody ContractActionRequest req) {
        return new ApiResponse<>(200, "ok.contract.cancelled", service.cancel(id, req));
    }

    /** Ejecuta ahora las tareas de vencimiento, inicio de renovaciones y avisos (el job las corre cada hora). */
    @PostMapping("/maintenance/run")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<MaintenanceResponse> runMaintenance() {
        return new ApiResponse<>(200, "ok.contract.maintenance", lifecycle.run());
    }
}
