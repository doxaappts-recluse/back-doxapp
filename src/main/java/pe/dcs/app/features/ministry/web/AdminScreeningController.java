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
import pe.dcs.app.features.ministry.service.ScreeningService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

/** M11 · Verificación de antecedentes (screening). Todo exige la acción Q, que se delega aparte de la edición de ministerios. */
@RestController
@RequestMapping("/api/v1/admin/screenings")
@ModuleAccess(module = "MINISTRY", action = Action.Q)
@RequiredArgsConstructor
public class AdminScreeningController {

    private final ScreeningService service;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    public ApiResponse<PageResponse<MinistryDtos.ScreeningResponse>> search(@RequestBody(required = false) MinistryDtos.ScreeningSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.actor(), resolver.current(), req));
    }

    @GetMapping("/persons/{personId}")
    public ApiResponse<List<MinistryDtos.ScreeningResponse>> ofPerson(@PathVariable UUID personId) {
        return new ApiResponse<>(200, "ok.common.sent", service.ofPerson(resolver.actor(), resolver.current(), personId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MinistryDtos.ScreeningResponse> create(@RequestBody MinistryDtos.ScreeningRequest req) {
        return new ApiResponse<>(201, "ok.ministry.screeningSaved", service.create(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}")
    public ApiResponse<MinistryDtos.ScreeningResponse> update(@PathVariable UUID id, @RequestBody MinistryDtos.ScreeningRequest req) {
        return new ApiResponse<>(200, "ok.ministry.screeningSaved", service.update(resolver.actor(), resolver.current(), id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(resolver.actor(), resolver.current(), id);
        return new ApiResponse<>(200, "ok.common.deleted", null);
    }
}
