package pe.dcs.app.features.ministry.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.ministry.dto.MinistryDtos;
import pe.dcs.app.features.ministry.service.MinistryService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

/** M11 · Estructura ministerial (módulo base MINISTRY): V consultar, C crear, E editar (incluye cargos), D eliminar, S activar/inactivar. Cambiar la estructura es del administrador de la organización. */
@RestController
@RequestMapping("/api/v1/admin/ministries")
@ModuleAccess(module = "MINISTRY", action = Action.V)
@RequiredArgsConstructor
public class AdminMinistryController {

    private final MinistryService service;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    public ApiResponse<PageResponse<MinistryDtos.MinistryResponse>> search(@RequestBody(required = false) MinistryDtos.MinistrySearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @GetMapping("/{id}")
    public ApiResponse<MinistryDtos.MinistryResponse> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MinistryDtos.MinistryResponse> create(@RequestBody MinistryDtos.MinistryRequest req) {
        return new ApiResponse<>(201, "ok.ministry.created", service.create(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}")
    public ApiResponse<MinistryDtos.MinistryResponse> update(@PathVariable UUID id, @RequestBody MinistryDtos.MinistryUpdate req) {
        return new ApiResponse<>(200, "ok.ministry.updated", service.update(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<MinistryDtos.MinistryResponse> activate(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.ministry.activated", service.setStatus(resolver.actor(), resolver.current(), id, true));
    }

    @PostMapping("/{id}/inactivate")
    public ApiResponse<MinistryDtos.MinistryResponse> inactivate(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.ministry.inactivated", service.setStatus(resolver.actor(), resolver.current(), id, false));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(resolver.actor(), resolver.current(), id);
        return new ApiResponse<>(200, "ok.common.deleted", null);
    }

    @GetMapping("/{id}/positions")
    public ApiResponse<List<MinistryDtos.PositionResponse>> positions(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.positionsOf(resolver.current(), id));
    }

    @PostMapping("/{id}/positions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MinistryDtos.MinistryResponse> addPosition(@PathVariable UUID id, @RequestBody MinistryDtos.PositionRequest req) {
        return new ApiResponse<>(201, "ok.ministry.positionSaved", service.addPosition(resolver.actor(), resolver.current(), id, req));
    }

    @PutMapping("/{id}/positions/{positionId}")
    public ApiResponse<MinistryDtos.MinistryResponse> updatePosition(@PathVariable UUID id, @PathVariable UUID positionId, @RequestBody MinistryDtos.PositionRequest req) {
        return new ApiResponse<>(200, "ok.ministry.positionSaved", service.updatePosition(resolver.actor(), resolver.current(), id, positionId, req));
    }

    @DeleteMapping("/{id}/positions/{positionId}")
    public ApiResponse<MinistryDtos.MinistryResponse> deletePosition(@PathVariable UUID id, @PathVariable UUID positionId) {
        return new ApiResponse<>(200, "ok.common.deleted", service.deletePosition(resolver.actor(), resolver.current(), id, positionId));
    }
}
