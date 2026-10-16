package pe.dcs.app.features.doctemplate.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.dcs.app.features.doctemplate.dto.TemplateDtos;
import pe.dcs.app.features.doctemplate.service.SignatoryService;
import pe.dcs.app.features.doctemplate.service.TemplateSupport;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.Exceptions;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/** M18 · Firmantes ({@code DOC_TEMPLATES}). */
@RestController
@RequestMapping("/api/v1/admin/signatories")
@RequiredArgsConstructor
public class AdminSignatoryController {

    private static final String MODULE = TemplateSupport.MODULE;

    private final SignatoryService service;
    private final AccessScopeResolver resolver;

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<TemplateDtos.SignatoryView>> list(@RequestParam(required = false, defaultValue = "false") boolean onlyActive) {
        return new ApiResponse<>(200, "ok.common.sent", service.list(resolver.current(), onlyActive));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<TemplateDtos.SignatoryView> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<TemplateDtos.SignatoryView> create(@RequestBody TemplateDtos.SignatoryRequest req) {
        return new ApiResponse<>(201, "ok.template.signatorySaved", service.create(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<TemplateDtos.SignatoryView> update(@PathVariable UUID id, @RequestBody TemplateDtos.SignatoryRequest req) {
        return new ApiResponse<>(200, "ok.template.signatorySaved", service.update(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping(value = "/{id}/signature", consumes = "multipart/form-data")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<TemplateDtos.SignatoryView> signature(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        try {
            return new ApiResponse<>(200, "ok.template.signatorySaved", service.uploadSignature(resolver.actor(), resolver.current(), id, file.getContentType(), file.getBytes()));
        } catch (IOException e) {
            throw new Exceptions("error.template.imageInvalid", HttpStatus.BAD_REQUEST);
        }
    }

    @PatchMapping("/{id}/status")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<TemplateDtos.SignatoryView> status(@PathVariable UUID id, @RequestParam boolean active) {
        return new ApiResponse<>(200, "ok.template.signatorySaved", service.setActive(resolver.actor(), resolver.current(), id, active));
    }
}
