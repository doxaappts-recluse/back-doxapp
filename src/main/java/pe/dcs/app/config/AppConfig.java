package pe.dcs.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.jwt.JwtAuthentication;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class AppConfig {

    /** Reloj único (UTC). Se inyecta para poder fijar el tiempo en las pruebas. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** created_by / updated_by = id del titular autenticado (staffId o personId). Sin autenticación (bootstrap/jobs): vacío. */
    @Bean
    public AuditorAware<UUID> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthentication jwt) {
                AuthenticatedActor actor = jwt.getPrincipal();
                return Optional.ofNullable(actor.ownerId());
            }
            return Optional.empty();
        };
    }
}
