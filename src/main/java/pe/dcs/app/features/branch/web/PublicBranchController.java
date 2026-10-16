package pe.dcs.app.features.branch.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.branch.dto.PublicBranchResponse;
import pe.dcs.app.features.branch.service.BranchService;
import pe.dcs.app.security.authz.PublicEndpoint;
import pe.dcs.app.shared.storage.FileStorageService;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** M04 · Sedes públicas de una organización (sin sesión): solo nombre, dirección, horarios y contacto público. Sin envoltorio ApiResponse. */
@RestController
@PublicEndpoint
@RequestMapping("/api/v1/public/orgs/{slug}/branches")
@RequiredArgsConstructor
public class PublicBranchController {

    private final BranchService service;

    @GetMapping
    public ResponseEntity<List<PublicBranchResponse>> list(@PathVariable String slug) {
        return service.publicList(slug)
                .map(body -> ResponseEntity.ok().cacheControl(CacheControl.noCache()).contentType(MediaType.APPLICATION_JSON).body(body))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{code}/logo")
    public ResponseEntity<byte[]> logo(@PathVariable String slug, @PathVariable String code) {
        FileStorageService.StoredFile f = service.publicLogo(slug, code).orElse(null);
        if (f == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
                .header("Cross-Origin-Resource-Policy", "cross-origin")
                .body(f.data());
    }
}
