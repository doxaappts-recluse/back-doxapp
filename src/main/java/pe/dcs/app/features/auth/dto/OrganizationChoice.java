package pe.dcs.app.features.auth.dto;

/** Opción del selector del login directo. {@code slug} "@platform" = personal de plataforma (name nulo). */
public record OrganizationChoice(String slug, String name) {
}
