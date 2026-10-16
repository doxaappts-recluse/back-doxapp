package pe.dcs.app.features.person.web;

import jakarta.validation.Valid;
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
import pe.dcs.app.features.person.dto.HouseholdDtos;
import pe.dcs.app.features.person.service.HouseholdService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/** M06 · Hogares (N2/N3): V consultar, C crear, E editar y manejar integrantes, D disolver el hogar. */
@RestController
@RequestMapping("/api/v1/admin/households")
@RequiredArgsConstructor
public class AdminHouseholdController {

    private static final String MODULE = "FAMILY";

    private final HouseholdService service;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<HouseholdDtos.Summary>> search(@RequestBody(required = false) HouseholdDtos.Search req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<HouseholdDtos.Response> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<HouseholdDtos.Response> create(@Valid @RequestBody HouseholdDtos.Request req) {
        return new ApiResponse<>(201, "ok.household.created", service.create(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<HouseholdDtos.Response> update(@PathVariable UUID id, @Valid @RequestBody HouseholdDtos.Request req) {
        return new ApiResponse<>(200, "ok.household.updated", service.update(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping("/{id}/members")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<HouseholdDtos.Response> addMember(@PathVariable UUID id, @RequestBody HouseholdDtos.MemberRequest req) {
        return new ApiResponse<>(200, "ok.household.memberAdded", service.addMember(resolver.actor(), resolver.current(), id, req));
    }

    @PutMapping("/{id}/members/{personId}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<HouseholdDtos.Response> updateMember(@PathVariable UUID id, @PathVariable UUID personId, @RequestBody HouseholdDtos.MemberRequest req) {
        return new ApiResponse<>(200, "ok.household.memberUpdated", service.updateMember(resolver.actor(), resolver.current(), id, personId, req));
    }

    @DeleteMapping("/{id}/members/{personId}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<HouseholdDtos.Response> removeMember(@PathVariable UUID id, @PathVariable UUID personId) {
        return new ApiResponse<>(200, "ok.household.memberRemoved", service.removeMember(resolver.actor(), resolver.current(), id, personId));
    }

    @PutMapping("/{id}/head")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<HouseholdDtos.Response> changeHead(@PathVariable UUID id, @RequestBody HouseholdDtos.HeadRequest req) {
        return new ApiResponse<>(200, "ok.household.headChanged", service.changeHead(resolver.actor(), resolver.current(), id, req));
    }

    @DeleteMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.D)
    public ApiResponse<Void> dissolve(@PathVariable UUID id) {
        service.dissolve(resolver.actor(), resolver.current(), id);
        return new ApiResponse<>(200, "ok.household.dissolved", null);
    }
}
