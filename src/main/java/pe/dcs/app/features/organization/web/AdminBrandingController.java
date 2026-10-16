package pe.dcs.app.features.organization.web;

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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.dcs.app.features.organization.dto.BrandingResponse;
import pe.dcs.app.features.organization.dto.BrandingUpdateRequest;
import pe.dcs.app.features.organization.service.BrandingKind;
import pe.dcs.app.features.organization.service.BrandingService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.Exceptions;

/** M02 · Marca de la organización (solo N2: ORG_ADMIN). */
@RestController
@RequestMapping("/api/v1/admin/organization/branding")
@RequiredArgsConstructor
public class AdminBrandingController {

    private static final String MODULE = "ORG_BRANDING";

    private final BrandingService service;
    private final AccessScopeResolver resolver;

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<BrandingResponse> get() {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.actor()));
    }

    @PutMapping
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<BrandingResponse> update(@Valid @RequestBody BrandingUpdateRequest req) {
        BrandingResponse res = service.update(req, resolver.actor());
        return new ApiResponse<>(200, res.contrastAdjusted() ? "msg.org.contrastAdjusted" : "ok.org.brandingSaved", res);
    }

    /** Vuelve a la marca por defecto. */
    @DeleteMapping
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<BrandingResponse> reset() {
        return new ApiResponse<>(200, "ok.org.brandingReset", service.reset(resolver.actor()));
    }

    @PostMapping(value = "/files/{kind}", consumes = "multipart/form-data")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<BrandingResponse> upload(@PathVariable String kind, @RequestPart("file") MultipartFile file) {
        return new ApiResponse<>(200, "ok.org.brandingSaved", service.uploadFile(kind(kind), file, resolver.actor()));
    }

    @DeleteMapping("/files/{kind}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<BrandingResponse> deleteFile(@PathVariable String kind) {
        return new ApiResponse<>(200, "ok.org.brandingSaved", service.deleteFile(kind(kind), resolver.actor()));
    }

    private static BrandingKind kind(String path) {
        return BrandingKind.fromPath(path).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }
}
