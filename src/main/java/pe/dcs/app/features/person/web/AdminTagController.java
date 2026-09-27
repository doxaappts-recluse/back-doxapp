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
import pe.dcs.app.features.person.dto.TagDtos;
import pe.dcs.app.features.person.service.TagService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

/** M06 · Etiquetas de personas (N2/N3): V consultar, C crear, E renombrar/recolorear, D eliminar. */
@RestController
@RequestMapping("/api/v1/admin/tags")
@RequiredArgsConstructor
public class AdminTagController {

    private static final String MODULE = "PERSON_TAGS";

    private final TagService service;
    private final AccessScopeResolver resolver;

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<TagDtos.Response>> list() {
        return new ApiResponse<>(200, "ok.common.sent", service.list(resolver.current()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<TagDtos.Response> create(@Valid @RequestBody TagDtos.Request req) {
        return new ApiResponse<>(201, "ok.tag.created", service.create(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<TagDtos.Response> update(@PathVariable UUID id, @Valid @RequestBody TagDtos.Request req) {
        return new ApiResponse<>(200, "ok.tag.updated", service.update(resolver.actor(), resolver.current(), id, req));
    }

    @DeleteMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.D)
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(resolver.actor(), resolver.current(), id);
        return new ApiResponse<>(200, "ok.tag.deleted", null);
    }
}
