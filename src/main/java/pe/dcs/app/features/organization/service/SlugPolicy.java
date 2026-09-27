package pe.dcs.app.features.organization.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.organization.domain.OrganizationRepository;
import pe.dcs.app.features.organization.domain.SlugRedirectRepository;
import pe.dcs.app.util.Exceptions;

import java.time.Clock;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * M02 [V3] · Reglas del slug (identificador en la URL de la organización): minúsculas, 3–30 caracteres a-z 0-9 y guion
 * (sin guion al inicio ni al final), no reservado y único (también frente a slugs antiguos que aún redirigen).
 */
@Component
@RequiredArgsConstructor
public class SlugPolicy {

    private static final Pattern FORMAT = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{1,28})[a-z0-9]$");

    static final Set<String> RESERVED = Set.of("platform", "api", "admin", "portal", "o", "www", "app", "login", "auth",
            "public", "static", "assets", "support", "soporte", "doxapp", "help", "docs", "status", "mail", "ftp", "root", "system");

    private final OrganizationRepository organizations;
    private final SlugRedirectRepository redirects;
    private final Clock clock;

    /** Devuelve el slug normalizado (minúsculas) o lanza 400/409 con la clave de error que corresponda. */
    public String check(String raw, UUID excludeOrganizationId) {
        String slug = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!FORMAT.matcher(slug).matches()) {
            throw new Exceptions("error.org.slugFormat", HttpStatus.BAD_REQUEST);
        }
        if (RESERVED.contains(slug)) {
            throw new Exceptions("error.org.slugReserved", HttpStatus.BAD_REQUEST, slug);
        }
        if (organizations.slugTaken(slug, excludeOrganizationId)
                || redirects.reservedByOther(slug, clock.instant(), excludeOrganizationId)) {
            throw new Exceptions("error.org.slugTaken", HttpStatus.CONFLICT, slug);
        }
        return slug;
    }
}
