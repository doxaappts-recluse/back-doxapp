package pe.dcs.app.features.attendance.web;

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
import pe.dcs.app.features.attendance.dto.AttendanceDtos;
import pe.dcs.app.features.attendance.service.ChurchServiceService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/** M09 · Cultos (plantillas): V consultar, C crear, E editar, S activar/desactivar, D borrar uno sin sesiones. */
@RestController
@RequestMapping("/api/v1/admin/church-services")
@RequiredArgsConstructor
public class AdminChurchServiceController {

    private static final String MODULE = "ATTENDANCE";

    private final ChurchServiceService service;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<AttendanceDtos.ServiceResponse>> search(@RequestBody(required = false) AttendanceDtos.ServiceSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<AttendanceDtos.ServiceResponse> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<AttendanceDtos.ServiceResponse> create(@RequestBody AttendanceDtos.ServiceRequest req) {
        return new ApiResponse<>(201, "ok.attendance.serviceSaved", service.create(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<AttendanceDtos.ServiceResponse> update(@PathVariable UUID id, @RequestBody AttendanceDtos.ServiceRequest req) {
        return new ApiResponse<>(200, "ok.attendance.serviceSaved", service.update(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping("/{id}/status")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<AttendanceDtos.ServiceResponse> status(@PathVariable UUID id, @RequestBody AttendanceDtos.StatusRequest req) {
        return new ApiResponse<>(200, "ok.attendance.serviceSaved", service.setStatus(resolver.actor(), resolver.current(), id, req == null ? null : req.status()));
    }

    @DeleteMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.D)
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(resolver.current(), id);
        return new ApiResponse<>(200, "ok.attendance.serviceDeleted", null);
    }
}
