package pe.dcs.app.security;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.dcs.app.features.auth.dto.AuthResponse;
import pe.dcs.app.features.auth.dto.AuthStatus;
import pe.dcs.app.features.auth.dto.LoginRequest;
import pe.dcs.app.features.auth.service.AuthenticationService;
import pe.dcs.app.features.auth.service.ClientInfo;
import pe.dcs.app.util.Exceptions;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * [#4 / #11 RLS y autenticación, auditoría 2026-10-09] Prueba de regresión automatizada, contra un Postgres
 * real (Testcontainers) con las 39 migraciones reales y los datos de prueba de {@code dev/import.sql}
 * (mismas organizaciones y usuarios que se verificaron a mano el 2026-10-09: demo, esperanza, sincontrato,
 * suspendida; marta@sincontrato.test, ana@demo.test, etc.).
 *
 * <p>Corre como un rol de Postgres SIN privilegios de superusuario ({@code doxapp_app}, NOSUPERUSER
 * NOBYPASSRLS) y dueño de todas las tablas (igual que en producción) — el rol admin del contenedor de
 * Testcontainers, como el {@code postgres} del sandbox de desarrollo, tiene BYPASSRLS y daría falsos
 * positivos si se usara para las pruebas.
 *
 * <p>Dos niveles de prueba, a propósito:
 * <ul>
 *   <li><b>Nivel SQL directo</b> ({@code rawConnection()}): una conexión JDBC manual, SIN pasar por el bean
 *       {@code dataSource} de Spring (que {@link RlsAwareDataSource} envuelve). Prueba exactamente lo que
 *       Postgres hace con las políticas de la migración V39, sin que ninguna lógica del back pueda
 *       maquillar el resultado.</li>
 *   <li><b>Nivel de aplicación</b> ({@code authService}): usa el bean real de Spring. Es la prueba de
 *       regresión específica para el bug encontrado el 2026-10-09 — {@code AuthenticationService} es
 *       {@code @Transactional} a nivel de clase, así que fijar el contexto DENTRO del método (en vez de
 *       con {@link RlsAwareDataSource#applyNow}) llega demasiado tarde para esa misma conexión. Si alguien
 *       revierte ese fix sin querer, este test falla con un 401 falso en vez de la selección de contexto.</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest
class RlsIsolationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String APP_USER = "doxapp_app";
    private static final String APP_PASSWORD = "doxapp_app_test_pw";

    // IDs y conteos de pe.dcs.app dev/import.sql — si ese archivo cambia, estas constantes también.
    private static final String DEMO_ORG = "11111111-1111-1111-1111-111111111111";
    private static final String ESPERANZA_ORG = "12222222-2222-2222-2222-222222222222";
    private static final String SINCONTRATO_ORG = "13333333-3333-3333-3333-333333333333";
    private static final String SUSPENDIDA_ORG = "14444444-4444-4444-4444-444444444444";

    @Autowired
    private AuthenticationService authService;

    /** Rol NO superusuario, dueño de las tablas (como en producción) — se crea antes de que Spring arranque
     *  y aplique las migraciones, para que sea ese rol el que las ejecute (y por lo tanto su dueño). */
    @BeforeAll
    static void createNonSuperuserAppRole() throws SQLException {
        try (Connection admin = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement st = admin.createStatement()) {
            st.execute("CREATE ROLE " + APP_USER + " LOGIN PASSWORD '" + APP_PASSWORD + "' NOSUPERUSER NOBYPASSRLS");
            st.execute("GRANT ALL ON SCHEMA public TO " + APP_USER);
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_USER);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
    }

    private Connection rawConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), APP_USER, APP_PASSWORD);
    }

    private int countWithContext(String sql, String orgId, boolean bypass) throws SQLException {
        try (Connection c = rawConnection(); Statement st = c.createStatement()) {
            st.execute("SET app.current_org = " + (orgId == null ? "''" : "'" + orgId + "'"));
            st.execute("SET app.rls_bypass = " + (bypass ? "'true'" : "'false'"));
            try (ResultSet rs = st.executeQuery(sql)) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // ------------------------------------------------------------------ nivel SQL (política de V39)

    @Test
    void rls_personIsolatedPorOrganizacion() throws SQLException {
        // demo trae además las personas de asistencia/visitas de los módulos M13-M24 (no solo las 4 originales).
        assertThat(countWithContext("select count(*) from person", DEMO_ORG, false)).isEqualTo(17);
        assertThat(countWithContext("select count(*) from person", ESPERANZA_ORG, false)).isEqualTo(3);
        assertThat(countWithContext("select count(*) from person", SINCONTRATO_ORG, false)).isEqualTo(1);
        assertThat(countWithContext("select count(*) from person", SUSPENDIDA_ORG, false)).isEqualTo(1);
    }

    @Test
    void rls_userAccessIsolatedPorOrganizacion() throws SQLException {
        assertThat(countWithContext("select count(*) from user_access", DEMO_ORG, false)).isEqualTo(5);
        assertThat(countWithContext("select count(*) from user_access", SINCONTRATO_ORG, false)).isEqualTo(1);
    }

    @Test
    void rls_finMovementIsolatedPorOrganizacion() throws SQLException {
        // los 5 movimientos de dev/import.sql son todos de "demo"; sincontrato no tiene ninguno.
        assertThat(countWithContext("select count(*) from fin_movement", DEMO_ORG, false)).isEqualTo(5);
        assertThat(countWithContext("select count(*) from fin_movement", SINCONTRATO_ORG, false)).isEqualTo(0);
    }

    @Test
    void rls_cierraEnFalsoSinContexto() throws SQLException {
        // ni app.current_org ni app.rls_bypass fijados (vacío/false): 0 filas, nunca una excepción ni todas las filas.
        assertThat(countWithContext("select count(*) from person", null, false)).isZero();
        assertThat(countWithContext("select count(*) from user_access", null, false)).isZero();
        assertThat(countWithContext("select count(*) from fin_movement", null, false)).isZero();
    }

    @Test
    void rls_credentialNullOrgSoloVisibleSinContextoDeOrganizacion() throws SQLException {
        // las 4 credenciales de personal de plataforma (organization_id IS NULL) solo se ven SIN organización activa.
        assertThat(countWithContext("select count(*) from credential where organization_id is null", null, false)).isEqualTo(4);
        // con un contexto de organización concreto, esas mismas filas de plataforma quedan fuera (no son de esa org).
        assertThat(countWithContext("select count(*) from credential where organization_id is null", DEMO_ORG, false)).isZero();
    }

    @Test
    void rls_bypassVeTodasLasOrganizaciones() throws SQLException {
        // el total real (demo 17 + esperanza 3 + sincontrato 1 + suspendida 1) solo es visible con el bypass de jobs/sistema.
        assertThat(countWithContext("select count(*) from person", null, true)).isEqualTo(22);
    }

    // ------------------------------------------------------------------ nivel aplicación (regresión del bug 2026-10-09)

    @Test
    void login_porSlugFuncionaParaCadaOrganizacion() {
        // Marta (sincontrato, ORG_ADMIN sin contrato activo: igual puede entrar) — este es el caso EXACTO que
        // falló con un 401 falso antes del fix de RlsAwareDataSource.applyNow en AuthenticationService.
        AuthResponse marta = authService.loginOrganization("sincontrato",
                new LoginRequest("marta@sincontrato.test", "Demo12345xy", null), ClientInfo.unknown());
        assertThat(marta.status()).isEqualTo(AuthStatus.CONTEXT_REQUIRED);

        // Ana existe con la MISMA contraseña en "demo" Y en "esperanza" — el slug debe aislar correctamente cuál.
        AuthResponse ana = authService.loginOrganization("demo",
                new LoginRequest("ana@demo.test", "Demo12345xy", null), ClientInfo.unknown());
        assertThat(ana.status()).isEqualTo(AuthStatus.CONTEXT_REQUIRED);
    }

    @Test
    void login_noMezclaCredencialesEntreOrganizaciones() {
        // la credencial de Ana es de "demo"/"esperanza", no de "sincontrato": debe fallar genérico, no entrar.
        Exceptions ex = assertThrows(Exceptions.class, () -> authService.loginOrganization("sincontrato",
                new LoginRequest("ana@demo.test", "Demo12345xy", null), ClientInfo.unknown()));
        assertThat(ex.getCode()).isEqualTo("error.auth.invalidCredentials");
    }
}
