package pe.dcs.app.features.volunteer.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.volunteer.dto.VolunteerDtos;
import pe.dcs.app.features.volunteer.service.AvailabilityService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/** M11b · No disponibilidad de las personas [V10] (registrada por la administración; el autoservicio del voluntario llega en el portal, M24). */
@RestController
@RequestMapping("/api/v1/admin/availability")
@ModuleAccess(module = "VOLUNTEER_SCHEDULING", action = Action.V)
@RequiredArgsConstructor
public class AdminAvailabilityController {

    private final AvailabilityService service;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    public ApiResponse<PageResponse<VolunteerDtos.AvailabilityResponse>> search(@RequestBody(required = false) VolunteerDtos.AvailabilitySearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VolunteerDtos.AvailabilityResponse> create(@RequestBody VolunteerDtos.AvailabilityRequest req) {
        return new ApiResponse<>(201, "ok.shift.availabilitySaved", service.create(resolver.actor(), resolver.current(), req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(resolver.actor(), resolver.current(), id);
        return new ApiResponse<>(200, "ok.common.deleted", null);
    }
}
