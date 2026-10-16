package pe.dcs.app.security;

/**
 * [#4, auditoría 2026-10-09] ThreadLocal con el {@code organizationId} del actor autenticado de la petición en
 * curso, para que {@link RlsAwareDataSource} lo use al abrir cada conexión NUEVA. Lo fija {@link OrgContextFilter}
 * por cada request (desde el JWT) y SIEMPRE se limpia en su {@code finally}.
 *
 * <p>Esto cubre el camino normal: cualquier petición autenticada, desde que hay un token con organización fija.
 * Los pocos pasos de {@code AuthenticationService} que resuelven la organización (o necesitan verla en todas)
 * A MITAD de una transacción ya abierta —login por slug o directo, reseteo de contraseña, aceptar invitación,
 * elegir contexto— no pueden depender de este ThreadLocal (la conexión de esa transacción ya se abrió antes de
 * que el método pusiera nada aquí); usan {@link RlsAwareDataSource#applyNow} en su lugar, directo sobre la
 * conexión ya abierta. Ver el aviso en {@link RlsAwareDataSource}.
 */
public final class OrgContextHolder {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private OrgContextHolder() {
    }

    public static void set(String organizationId) {
        CURRENT.set(organizationId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** El organizationId del actor de esta petición, o {@code null} si no aplica (sin actor, o personal de
     *  plataforma sin acceso asistido — ver {@link OrgContextFilter}). */
    public static String currentOrgId() {
        return CURRENT.get();
    }
}
