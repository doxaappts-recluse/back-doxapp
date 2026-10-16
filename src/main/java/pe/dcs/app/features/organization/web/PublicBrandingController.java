package pe.dcs.app.features.organization.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.organization.dto.PublicBrandingResponse;
import pe.dcs.app.features.organization.service.BrandingKind;
import pe.dcs.app.features.organization.service.BrandingService;
import pe.dcs.app.security.authz.PublicEndpoint;
import pe.dcs.app.shared.storage.FileStorageService;

import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * M02 · Marca pública de una organización (login con la marca de la iglesia). Sin sesión: solo marca y contacto público.
 * El JSON va sin envoltorio ApiResponse, con ETag (304 si no cambió) y sin caché compartida; los archivos llevan
 * {@code ?v=revision} y se pueden cachear un año.
 */
@RestController
@PublicEndpoint
@RequestMapping("/api/v1/public/orgs/{slug}/branding")
@RequiredArgsConstructor
public class PublicBrandingController {

    private final BrandingService service;

    @GetMapping
    public ResponseEntity<PublicBrandingResponse> get(@PathVariable String slug,
                                                     @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        BrandingService.PublicResult r = service.publicBranding(slug)
                .orElse(null);
        if (r == null) {
            return ResponseEntity.notFound().build();
        }
        if (r.redirectSlug() != null) {
            return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                    .location(URI.create("/api/v1/public/orgs/" + r.redirectSlug() + "/branding"))
                    .cacheControl(CacheControl.noCache())
                    .build();
        }
        if (ifNoneMatch != null && r.etag().equals(ifNoneMatch.replaceFirst("^W/", ""))) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(r.etag()).cacheControl(CacheControl.noCache()).build();
        }
        return ResponseEntity.ok().eTag(r.etag()).cacheControl(CacheControl.noCache())
                .contentType(MediaType.APPLICATION_JSON).body(r.body());
    }

    @GetMapping("/files/{kind}")
    public ResponseEntity<byte[]> file(@PathVariable String slug, @PathVariable String kind) {
        BrandingKind k = BrandingKind.fromPath(kind).orElse(null);
        if (k == null) {
            return ResponseEntity.notFound().build();
        }
        FileStorageService.StoredFile f = service.publicFile(slug, k).orElse(null);
        if (f == null) {
            return ResponseEntity.notFound().build();
        }
        // el SVG ya está saneado al subirlo; CSP sandbox evita que ejecute nada si se abre directamente
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
                .header("Cross-Origin-Resource-Policy", "cross-origin")
                .body(f.data());
    }
}
