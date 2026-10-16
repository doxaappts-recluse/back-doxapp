package pe.dcs.app.features.catalog.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.catalog.dto.CatalogOption;
import pe.dcs.app.features.catalog.service.CatalogService;
import pe.dcs.app.security.authz.PublicEndpoint;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** M23 · Catálogos públicos (país, tipo de documento, idioma): sin sesión, cacheables. Otro tipo → 404. */
@PublicEndpoint
@RestController
@RequestMapping("/api/v1/public/catalogs")
@RequiredArgsConstructor
public class PublicCatalogController {

    private final CatalogService service;

    @GetMapping("/{type}")
    public ResponseEntity<List<CatalogOption>> list(@PathVariable String type, @RequestParam(required = false) String parent) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic()).body(service.publicItems(type, parent));
    }
}
