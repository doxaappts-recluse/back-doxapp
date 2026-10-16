package pe.dcs.app.features.organization.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.dcs.app.features.organization.domain.Organization;
import pe.dcs.app.features.organization.domain.OrganizationBranding;
import pe.dcs.app.features.organization.domain.OrganizationBrandingRepository;
import pe.dcs.app.features.organization.domain.OrganizationRepository;
import pe.dcs.app.features.organization.domain.OrganizationStatus;
import pe.dcs.app.features.organization.domain.SlugRedirectRepository;
import pe.dcs.app.features.organization.dto.BrandingResponse;
import pe.dcs.app.features.organization.dto.BrandingUpdateRequest;
import pe.dcs.app.features.organization.dto.PublicBrandingResponse;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.image.ImageInspector;
import pe.dcs.app.shared.storage.FileStorageService;
import pe.dcs.app.shared.vo.ColorUtils;
import pe.dcs.app.shared.vo.ContactValidator;
import pe.dcs.app.util.Exceptions;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * M02 · Identidad de marca de la organización: nombre visible, logos (claro/oscuro), favicon, fondo del login, colores,
 * textos de bienvenida y contacto público. Solo ORG_ADMIN edita (el módulo ORG_BRANDING es N2).
 *
 * <p>Reglas: [V9] imágenes PNG/WEBP/SVG ≤ 512 KB y ≤ 1024×1024, SVG saneado · [V10] colores #RRGGBB ·
 * [V12] si el texto blanco no alcanza contraste AA (4.5:1) sobre el color primario, se aplica un tono más oscuro
 * y se avisa. Lo público (sin sesión) es solo la marca y el contacto público.
 */
@Service
@RequiredArgsConstructor
public class BrandingService {

    static final String MODULE = "ORG_BRANDING";
    static final String ENTITY = "OrganizationBranding";
    private static final Pattern SOCIAL_KEY = Pattern.compile("^[a-z]{2,20}$");
    private static final int MAX_SOCIALS = 8;

    private final OrganizationRepository organizations;
    private final OrganizationBrandingRepository brandings;
    private final SlugRedirectRepository redirects;
    private final FileStorageService storage;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final Clock clock;

    // ---------------------------------------------------------------- editor (ORG_ADMIN)

    @Transactional(readOnly = true)
    public BrandingResponse get(AuthenticatedActor actor) {
        Organization o = organization(actor);
        return toResponse(o, brandings.findById(o.getId()).orElse(null));
    }

    @Transactional
    public BrandingResponse update(BrandingUpdateRequest req, AuthenticatedActor actor) {
        Organization o = organization(actor);
        OrganizationBranding b = brandings.findById(o.getId()).orElseGet(() -> fresh(o));
        Map<String, Object> changes = new LinkedHashMap<>();

        String display = blankToNull(req.displayName());
        if (display != null && (display.length() < 2 || display.length() > 60)) {
            throw new Exceptions("error.org.displayNameLength", HttpStatus.BAD_REQUEST);
        }
        track(changes, "displayName", b.getDisplayName(), display);
        b.setDisplayName(display);

        String primary = color(req.primaryColor());
        String secondary = color(req.secondaryColor());
        track(changes, "primaryColor", b.getPrimaryColor(), primary);
        track(changes, "secondaryColor", b.getSecondaryColor(), secondary);
        b.setPrimaryColor(primary);
        b.setSecondaryColor(secondary);

        String mail = hasText(req.contactEmail()) ? ContactValidator.normalizeEmail(req.contactEmail()) : null;
        if (mail != null && !ContactValidator.isValidEmail(mail)) {
            throw new Exceptions("error.common.emailInvalid", HttpStatus.BAD_REQUEST);
        }
        String phone = blankToNull(req.contactPhone());
        if (phone != null && !ContactValidator.isValidPhone(phone)) {
            throw new Exceptions("error.common.phoneInvalid", HttpStatus.BAD_REQUEST);
        }
        track(changes, "contactEmail", b.getContactEmail(), mail);
        track(changes, "contactPhone", b.getContactPhone(), phone);
        b.setContactEmail(mail);
        b.setContactPhone(phone);

        track(changes, "welcomeTextEs", b.getWelcomeTextEs(), blankToNull(req.welcomeTextEs()));
        track(changes, "welcomeTextEn", b.getWelcomeTextEn(), blankToNull(req.welcomeTextEn()));
        b.setWelcomeTextEs(blankToNull(req.welcomeTextEs()));
        b.setWelcomeTextEn(blankToNull(req.welcomeTextEn()));

        Map<String, String> socials = socials(req.socials());
        track(changes, "socials", b.getSocials(), socials);
        b.setSocials(socials);

        if (!changes.isEmpty()) {
            b.setRevision(b.getRevision() + 1);
        }
        brandings.saveAndFlush(b);
        audit.record(new AuditService.Command(MODULE, "BRANDING_UPDATE", ENTITY, o.getId(), o.getId(), null, changes));
        return toResponse(o, b);
    }

    @Transactional
    public BrandingResponse uploadFile(BrandingKind kind, MultipartFile file, AuthenticatedActor actor) {
        Organization o = organization(actor);
        if (file == null || file.isEmpty()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "file");
        }
        if (file.getSize() > ImageInspector.MAX_BYTES) {
            throw new Exceptions("error.common.fileSize", HttpStatus.BAD_REQUEST, "512 KB");
        }
        ImageInspector.Inspected img;
        try {
            img = ImageInspector.inspect(file.getBytes());
        } catch (ImageInspector.InvalidImageException ex) {
            throw invalidImage(ex);
        } catch (IOException ex) {
            throw new Exceptions("error.common.storage", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        OrganizationBranding b = brandings.findById(o.getId()).orElseGet(() -> fresh(o));
        int rev = b.getRevision() + 1;
        String oldKey = keyOf(b, kind);
        String newKey = "org/" + o.getId() + "/branding/" + kind.path + "-" + rev + "." + img.kind().extension;
        storage.put(newKey, img.data(), img.kind().contentType);
        setKey(b, kind, newKey);
        b.setRevision(rev);
        brandings.saveAndFlush(b);
        if (oldKey != null && !oldKey.equals(newKey)) {
            storage.delete(oldKey);
        }
        audit.record(new AuditService.Command(MODULE, "FILE_UPLOAD", ENTITY, o.getId(), o.getId(), null,
                diff("kind", kind.path, "type", img.kind().name(), "bytes", img.data().length)));
        return toResponse(o, b);
    }

    @Transactional
    public BrandingResponse deleteFile(BrandingKind kind, AuthenticatedActor actor) {
        Organization o = organization(actor);
        OrganizationBranding b = brandings.findById(o.getId()).orElse(null);
        if (b == null || keyOf(b, kind) == null) {
            return toResponse(o, b);
        }
        String old = keyOf(b, kind);
        setKey(b, kind, null);
        b.setRevision(b.getRevision() + 1);
        brandings.saveAndFlush(b);
        storage.delete(old);
        audit.record(new AuditService.Command(MODULE, "FILE_DELETE", ENTITY, o.getId(), o.getId(), null, diff("kind", kind.path)));
        return toResponse(o, b);
    }

    /** Vuelve a la marca por defecto de DoxApp (borra datos y archivos). */
    @Transactional
    public BrandingResponse reset(AuthenticatedActor actor) {
        Organization o = organization(actor);
        OrganizationBranding b = brandings.findById(o.getId()).orElse(null);
        if (b == null) {
            return toResponse(o, null);
        }
        for (BrandingKind k : BrandingKind.values()) {
            String key = keyOf(b, k);
            if (key != null) {
                storage.delete(key);
            }
        }
        int rev = b.getRevision() + 1;
        b.setDisplayName(null);
        b.setLogoLightKey(null);
        b.setLogoDarkKey(null);
        b.setFaviconKey(null);
        b.setLoginBackgroundKey(null);
        b.setPrimaryColor(null);
        b.setSecondaryColor(null);
        b.setWelcomeTextEs(null);
        b.setWelcomeTextEn(null);
        b.setContactEmail(null);
        b.setContactPhone(null);
        b.setSocials(new LinkedHashMap<>());
        b.setRevision(rev);
        brandings.saveAndFlush(b);
        audit.record(new AuditService.Command(MODULE, "RESET", ENTITY, o.getId(), o.getId(), null, diff("revision", rev)));
        return toResponse(o, b);
    }

    // ---------------------------------------------------------------- público (sin sesión)

    /** Resultado de consultar la marca pública: la marca, un redirect al slug vigente, o nada. */
    public record PublicResult(PublicBrandingResponse body, String etag, String redirectSlug) {
    }

    @Transactional(readOnly = true)
    public Optional<PublicResult> publicBranding(String rawSlug) {
        String slug = rawSlug == null ? "" : rawSlug.trim().toLowerCase(Locale.ROOT);
        Optional<Organization> org = organizations.findBySlug(slug).filter(this::publiclyVisible);
        if (org.isPresent()) {
            Organization o = org.get();
            PublicBrandingResponse body = toPublic(o, brandings.findById(o.getId()).orElse(null));
            return Optional.of(new PublicResult(body, etag(body), null));
        }
        // [V8] slug antiguo → redirige al vigente durante 90 días
        return redirects.findByOldSlugAndUntilAtAfter(slug, clock.instant())
                .flatMap(r -> organizations.findById(r.getOrganizationId()))
                .filter(this::publiclyVisible)
                .map(o -> new PublicResult(null, null, o.getSlug()));
    }

    @Transactional(readOnly = true)
    public Optional<FileStorageService.StoredFile> publicFile(String rawSlug, BrandingKind kind) {
        return organizations.findBySlug(rawSlug == null ? "" : rawSlug.trim().toLowerCase(Locale.ROOT))
                .filter(this::publiclyVisible)
                .flatMap(o -> brandings.findById(o.getId()))
                .map(b -> keyOf(b, kind))
                .flatMap(storage::get);
    }

    // ---------------------------------------------------------------- mapeo y reglas

    private boolean publiclyVisible(Organization o) {
        return o.getStatus() != OrganizationStatus.DRAFT && o.getStatus() != OrganizationStatus.CLOSED;
    }

    private BrandingResponse toResponse(Organization o, OrganizationBranding b) {
        String primary = b == null ? null : b.getPrimaryColor();
        String effective = primary == null ? null : ColorUtils.accessible(primary);
        return new BrandingResponse(o.getSlug(), o.getName(), b == null ? null : b.getDisplayName(), primary,
                b == null ? null : b.getSecondaryColor(), effective, primary != null && !primary.equals(effective),
                primary == null ? 0 : Math.round(ColorUtils.contrastWithWhite(primary) * 100.0) / 100.0,
                b == null ? null : b.getWelcomeTextEs(), b == null ? null : b.getWelcomeTextEn(),
                b == null ? null : b.getContactEmail(), b == null ? null : b.getContactPhone(),
                b == null || b.getSocials() == null ? Map.of() : b.getSocials(),
                url(o, b, BrandingKind.LOGO_LIGHT), url(o, b, BrandingKind.LOGO_DARK),
                url(o, b, BrandingKind.FAVICON), url(o, b, BrandingKind.LOGIN_BACKGROUND),
                b == null ? 1 : b.getRevision());
    }

    private PublicBrandingResponse toPublic(Organization o, OrganizationBranding b) {
        String primary = b == null ? null : b.getPrimaryColor();
        String effective = primary == null ? null : ColorUtils.accessible(primary);
        String display = b != null && b.getDisplayName() != null ? b.getDisplayName() : o.getName();
        return new PublicBrandingResponse(o.getSlug(), o.getName(), display, primary, b == null ? null : b.getSecondaryColor(),
                effective, primary != null && !primary.equals(effective),
                b == null ? null : b.getWelcomeTextEs(), b == null ? null : b.getWelcomeTextEn(),
                b == null ? null : b.getContactEmail(), b == null ? null : b.getContactPhone(),
                b == null || b.getSocials() == null ? Map.of() : b.getSocials(), o.getDefaultLanguage(),
                url(o, b, BrandingKind.LOGO_LIGHT), url(o, b, BrandingKind.LOGO_DARK),
                url(o, b, BrandingKind.FAVICON), url(o, b, BrandingKind.LOGIN_BACKGROUND),
                b == null ? 1 : b.getRevision());
    }

    /** URL relativa a la API (el front le antepone el origen); {@code v} versiona la caché del navegador. */
    private static String url(Organization o, OrganizationBranding b, BrandingKind kind) {
        if (b == null || keyOf(b, kind) == null) {
            return null;
        }
        return "/api/v1/public/orgs/" + o.getSlug() + "/branding/files/" + kind.path + "?v=" + b.getRevision();
    }

    private String etag(PublicBrandingResponse body) {
        try {
            byte[] json = mapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
            return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json)).substring(0, 32) + "\"";
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Exceptions invalidImage(ImageInspector.InvalidImageException ex) {
        return switch (ex.getMessage()) {
            case "size" -> new Exceptions("error.common.fileSize", HttpStatus.BAD_REQUEST, "512 KB");
            case "type" -> new Exceptions("error.common.fileType", HttpStatus.BAD_REQUEST, "PNG, WEBP, SVG");
            default -> new Exceptions("error.org.logoInvalid", HttpStatus.BAD_REQUEST);
        };
    }

    private static String color(String raw) {
        if (!hasText(raw)) {
            return null;
        }
        String c = raw.trim();
        if (!ColorUtils.isHex(c)) {
            throw new Exceptions("error.org.colorInvalid", HttpStatus.BAD_REQUEST);
        }
        return ColorUtils.normalize(c);
    }

    private static Map<String, String> socials(Map<String, String> in) {
        Map<String, String> out = new LinkedHashMap<>();
        if (in == null) {
            return out;
        }
        for (Map.Entry<String, String> e : in.entrySet()) {
            String v = blankToNull(e.getValue());
            if (v == null) {
                continue;
            }
            String k = e.getKey() == null ? "" : e.getKey().trim().toLowerCase(Locale.ROOT);
            if (!SOCIAL_KEY.matcher(k).matches() || v.length() > 255 || !isHttpUrl(v) || out.size() >= MAX_SOCIALS) {
                throw new Exceptions("error.org.socialInvalid", HttpStatus.BAD_REQUEST, k);
            }
            out.put(k, v);
        }
        return out;
    }

    private static boolean isHttpUrl(String v) {
        try {
            URI u = URI.create(v);
            return ("http".equalsIgnoreCase(u.getScheme()) || "https".equalsIgnoreCase(u.getScheme())) && hasText(u.getHost());
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private Organization organization(AuthenticatedActor actor) {
        if (actor.organizationId() == null) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        Organization o = organizations.findById(actor.organizationId())
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        return o;
    }

    private static OrganizationBranding fresh(Organization o) {
        OrganizationBranding b = new OrganizationBranding();
        b.setOrganizationId(o.getId());
        return b;
    }

    private static String keyOf(OrganizationBranding b, BrandingKind k) {
        return switch (k) {
            case LOGO_LIGHT -> b.getLogoLightKey();
            case LOGO_DARK -> b.getLogoDarkKey();
            case FAVICON -> b.getFaviconKey();
            case LOGIN_BACKGROUND -> b.getLoginBackgroundKey();
        };
    }

    private static void setKey(OrganizationBranding b, BrandingKind k, String key) {
        switch (k) {
            case LOGO_LIGHT -> b.setLogoLightKey(key);
            case LOGO_DARK -> b.setLogoDarkKey(key);
            case FAVICON -> b.setFaviconKey(key);
            case LOGIN_BACKGROUND -> b.setLoginBackgroundKey(key);
        }
    }

    private static void track(Map<String, Object> changes, String field, Object before, Object after) {
        if (!java.util.Objects.equals(before, after)) {
            Map<String, Object> pair = new LinkedHashMap<>();
            pair.put("from", before == null ? null : before.toString());
            pair.put("to", after == null ? null : after.toString());
            changes.put(field, pair);
        }
    }

    private static Map<String, Object> diff(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            if (kv[i + 1] != null) {
                m.put((String) kv[i], kv[i + 1].toString());
            }
        }
        return m;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return hasText(s) ? s.trim() : null;
    }
}
