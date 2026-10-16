package pe.dcs.app.features.plan.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.plan.dto.PlanRequest;
import pe.dcs.app.features.plan.dto.PlanResponse;
import pe.dcs.app.features.plan.dto.PlanSearchRequest;
import pe.dcs.app.features.plan.dto.PlanStatusRequest;
import pe.dcs.app.features.plan.service.PlanService;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

/** M03 · Planes (N1). SYSTEM_ADMIN: V C E S · SYSTEM_SUPPORT: V. */
@RestController
@RequestMapping("/api/v1/platform/plans")
@RequiredArgsConstructor
public class PlatformPlanController {

    private static final String MODULE = "PLAN";

    private final PlanService service;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<PlanResponse>> search(@RequestBody(required = false) PlanSearchRequest req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(req));
    }

    /** Planes publicados: los que se pueden ofrecer en un contrato nuevo. */
    @GetMapping("/published")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<PlanResponse>> published() {
        return new ApiResponse<>(200, "ok.common.sent", service.published());
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PlanResponse> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<PlanResponse> create(@Valid @RequestBody PlanRequest req) {
        return new ApiResponse<>(201, "ok.plan.created", service.create(req));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<PlanResponse> update(@PathVariable UUID id, @Valid @RequestBody PlanRequest req) {
        return new ApiResponse<>(200, "ok.plan.updated", service.update(id, req));
    }

    @PutMapping("/{id}/status")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<PlanResponse> changeStatus(@PathVariable UUID id, @Valid @RequestBody PlanStatusRequest req) {
        PlanResponse res = service.changeStatus(id, req.status());
        return new ApiResponse<>(200, res.status().name().equals("RETIRED") ? "ok.plan.retired" : "ok.plan.published", res);
    }
}
