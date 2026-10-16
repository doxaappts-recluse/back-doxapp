package pe.dcs.app.security;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.web.context.request.RequestContextHolder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * [#4, auditoría 2026-10-09] RLS acotado (person, credential, user_access, fin_movement — ver migración V39).
 * Envuelve el pool de Hikari: cada conexión que sale de él queda, como primera instrucción de su transacción,
 * con las dos variables de sesión que usan esas 4 políticas:
 *
 * <ul>
 *   <li>{@code app.current_org} — el organizationId de la petición en curso, publicado por
 *       {@link OrgContextFilter}. Vacío si no aplica.</li>
 *   <li>{@code app.rls_bypass} — {@code 'true'} SOLO cuando la transacción corre SIN una petición HTTP detrás:
 *       jobs {@code @Scheduled}, el arranque de la aplicación, las migraciones de Flyway. Ninguna petición HTTP
 *       real puede producir ese estado — no hay manera de "fingir" que no hay petición desde afuera — así que
 *       es seguro tratarlo como contexto de confianza: es código del propio backend, nunca entrada de un
 *       usuario. Sin este bypass, jobs legítimos que recorren varias organizaciones a la vez (p. ej.
 *       {@code AccessExpiryService}, que vence accesos de TODAS las organizaciones en cada corrida) quedarían
 *       rotos por RLS.</li>
 * </ul>
 *
 * <p>Se usa {@code SET LOCAL}, no {@code SET}: el valor se borra solo al terminar la transacción (commit o
 * rollback), así que una conexión reciclada por el pool nunca puede heredar el contexto de la petición
 * anterior.
 *
 * <p>Se registra envolviendo el bean {@code "dataSource"} (ver {@link RlsDataSourceBeanPostProcessor}), así que
 * cubre por igual a JPA, {@code NamedParameterJdbcTemplate} y Flyway — los tres comparten el mismo pool.
 *
 * <p><b>Ojo con {@code @Transactional} a nivel de clase</b> (p. ej. {@code AuthenticationService}): el proxy de
 * Spring hace el {@code getConnection()} al ENTRAR al método, antes de que corra una sola línea del cuerpo — así
 * que fijar {@link OrgContextHolder} DENTRO del método llega demasiado tarde para esa conexión (ya se abrió con
 * lo que hubiera en el ThreadLocal al entrar, típicamente vacío). Para los pocos pasos de autenticación donde la
 * organización (o el bypass) solo se conoce a mitad del método — login por slug, login directo, reseteo de
 * contraseña, aceptar invitación, elegir contexto — hay que aplicar el cambio DIRECTAMENTE sobre la conexión ya
 * abierta de la transacción en curso con {@link #applyNow}, en vez de confiar en que una futura conexión lo
 * recoja del ThreadLocal.
 */
public class RlsAwareDataSource extends DelegatingDataSource {

    public RlsAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = super.getConnection();
        applyRlsContext(connection, OrgContextHolder.currentOrgId(),
                RequestContextHolder.getRequestAttributes() == null);
        return connection;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection connection = super.getConnection(username, password);
        applyRlsContext(connection, OrgContextHolder.currentOrgId(),
                RequestContextHolder.getRequestAttributes() == null);
        return connection;
    }

    /**
     * Aplica el contexto RLS a mitad de una transacción ya abierta (ver el aviso de arriba sobre
     * {@code @Transactional} a nivel de clase). Usa {@link DataSourceUtils#getConnection} para obtener la MISMA
     * conexión que Spring ya tiene ligada a la transacción en curso de este hilo (no abre una nueva), y la
     * libera con {@link DataSourceUtils#releaseConnection} sin cerrarla (Spring sigue siendo su dueño).
     */
    public static void applyNow(DataSource dataSource, String organizationId, boolean bypass) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            applyRlsContext(connection, organizationId, bypass);
        } catch (SQLException e) {
            throw new org.springframework.dao.DataAccessResourceFailureException(
                    "No se pudo fijar el contexto RLS (app.current_org / app.rls_bypass)", e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private static void applyRlsContext(Connection connection, String orgId, boolean bypass) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("SET LOCAL app.current_org = " + literal(orgId == null ? "" : orgId));
            st.execute("SET LOCAL app.rls_bypass = " + (bypass ? "'true'" : "'false'"));
        }
    }

    /** orgId siempre es un UUID.toString() generado por el propio back (nunca entrada cruda de un usuario),
     *  pero se escapa de todos modos: SET LOCAL no admite parámetros bind (`?`). */
    private static String literal(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
