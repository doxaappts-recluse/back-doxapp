package pe.dcs.app.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.dcs.app.features.auth.service.SecretCipher;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.regex.Pattern;

/**
 * SOLO DESARROLLO. Con {@code app.dev.reset-on-start=true} cada arranque deja la base local como nueva:
 * vacía el esquema (Flyway clean), aplica todas las migraciones y carga {@code classpath:dev/import.sql}.
 *
 * Por qué no {@code ddl-auto: create}: Hibernate crearía las tablas desde las entidades y perdería lo que solo viven en
 * las migraciones (CHECK, índices únicos parciales, catálogo de módulos), y además chocaría con Flyway. Esto da el mismo
 * efecto (base limpia + datos de prueba) con el esquema real.
 *
 * Salvaguardas: se niega a correr si la base no es local y Flyway exige {@code spring.flyway.clean-disabled=false}.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.dev.reset-on-start", havingValue = "true")
public class DevDatabaseReset {

    private static final Pattern LOCAL_URL = Pattern.compile("^jdbc:postgresql://(localhost|127\\.0\\.0\\.1|\\[::1])([:/].*)?$");
    /** Secreto TOTP fijo de desarrollo (base32). Se registra UNA vez en la app autenticadora y sirve tras cada reinicio. */
    public static final String DEV_MFA_SECRET = "DEVDOXAPPTESTSECRET234567ABCDEFG";
    public static final String DEV_PASSWORD = "Demo12345xy";

    @Bean
    FlywayMigrationStrategy devResetStrategy(DataSource dataSource, PasswordEncoder encoder, SecretCipher cipher,
                                             @Value("${spring.datasource.url}") String url,
                                             @Value("${app.dev.import-script:classpath:dev/import.sql}") String script) {
        return (Flyway flyway) -> {
            if (!LOCAL_URL.matcher(url).matches()) {
                throw new IllegalStateException("app.dev.reset-on-start solo se permite con una base local (localhost). URL: " + url);
            }
            log.warn("[DEV-RESET] Vaciando la base local y recargando migraciones + datos de prueba ({})", url);
            flyway.clean();
            flyway.migrate();
            try (Connection c = dataSource.getConnection()) {
                String sql = new String(new ClassPathResource(script.replace("classpath:", "")).getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8)
                        .replace("@@PWD_HASH@@", encoder.encode(DEV_PASSWORD))
                        .replace("@@MFA_SECRET@@", cipher.encrypt(DEV_MFA_SECRET));
                ScriptUtils.executeSqlScript(c, new EncodedResource(new org.springframework.core.io.ByteArrayResource(
                        sql.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
                c.commit();
                log.warn("[DEV-RESET] Datos de prueba cargados. Contraseña de todos los usuarios: {} · secreto TOTP: {}",
                        DEV_PASSWORD, DEV_MFA_SECRET);
            } catch (Exception e) {
                throw new IllegalStateException("No se pudo cargar " + script, e);
            }
        };
    }
}
