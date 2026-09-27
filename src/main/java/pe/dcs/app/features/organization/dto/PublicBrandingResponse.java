package pe.dcs.app.features.organization.dto;

import java.util.Map;

/** Lo único que se publica sin sesión: marca y contacto público. Nunca RUC, razón social ni datos internos. */
public record PublicBrandingResponse(
        String slug, String organizationName, String displayName, String primaryColor, String secondaryColor,
        String effectivePrimary, boolean contrastAdjusted, String welcomeTextEs, String welcomeTextEn,
        String contactEmail, String contactPhone, Map<String, String> socials, String defaultLanguage,
        String logoLightUrl, String logoDarkUrl, String faviconUrl, String loginBackgroundUrl, int revision
) {
}
