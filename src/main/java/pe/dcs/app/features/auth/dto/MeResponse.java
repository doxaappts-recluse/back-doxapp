package pe.dcs.app.features.auth.dto;

/** Quién soy y en qué contexto estoy (GET /me). {@code limited}: ORG_ADMIN sin contrato ACTIVE. */
public record MeResponse(UserInfo user, ContextInfo context, String contractState, boolean limited, Integer idleMinutes) {
}
