package pe.dcs.app.features.auth.dto;

/**
 * Resultado de un paso de autenticación:
 * AUTHENTICATED (token de acceso + refresh) · MFA_REQUIRED (falta el código) ·
 * SETUP_REQUIRED (cambiar contraseña / configurar MFA) · CONTEXT_REQUIRED (elegir acceso) ·
 * ORGANIZATION_REQUIRED (login directo: la contraseña sirve en varias organizaciones; falta elegir una).
 */
public enum AuthStatus {
    AUTHENTICATED, MFA_REQUIRED, SETUP_REQUIRED, CONTEXT_REQUIRED, ORGANIZATION_REQUIRED
}
