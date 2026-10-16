package pe.dcs.app.features.organization.dto;

import java.util.Map;

/**
 * Marca tal como la guarda la organización (para el editor). {@code effectivePrimary} es el color que realmente se aplica
 * (el elegido, o el mismo tono oscurecido si el texto blanco no llega al contraste AA 4.5:1) [V12].
 */
public record BrandingResponse(
        String slug, String organizationName, String displayName, String primaryColor, String secondaryColor,
        String effectivePrimary, boolean contrastAdjusted, double contrastRatio,
        String welcomeTextEs, String welcomeTextEn, String contactEmail, String contactPhone, Map<String, String> socials,
        String logoLightUrl, String logoDarkUrl, String faviconUrl, String loginBackgroundUrl, int revision
) {
}
