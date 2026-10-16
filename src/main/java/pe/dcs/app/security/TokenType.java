package pe.dcs.app.security;

/**
 * Tipo de token. Solo ACCESS abre la API; los demás son intermedios y de corta vida:
 * PRE_AUTH (contraseña válida, falta MFA) · SETUP (debe cambiar contraseña / configurar MFA) · CONTEXT (elegir acceso).
 */
public enum TokenType {
    ACCESS, PRE_AUTH, SETUP, CONTEXT
}
