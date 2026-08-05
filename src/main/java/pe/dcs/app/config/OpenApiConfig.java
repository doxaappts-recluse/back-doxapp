package pe.dcs.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de la documentación automática de la API (springdoc-openapi).
 *
 * Con esto habilitado, cada endpoint queda documentado automáticamente a
 * partir de las anotaciones ya existentes en los controllers/DTOs
 * (@RestController, @RequestMapping, @Valid, @NotNull/@NotBlank con sus
 * mensajes, etc.) — no requiere mantenimiento manual paralelo al código.
 *
 * Una vez la app está corriendo, la documentación queda disponible en:
 *   - UI interactiva: /swagger-ui.html
 *   - Spec JSON crudo:  /v3/api-docs
 *
 * El esquema de seguridad "bearerAuth" declarado acá permite usar el botón
 * "Authorize" de Swagger UI para pegar el JWT obtenido en /auth/login y
 * probar los endpoints protegidos directamente desde el navegador.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI doxAppOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("DOXAPP API")
                        .description("API REST del backend de DOXAPP: gestión de organizaciones, " +
                                "personas, eventos, finanzas, RRHH, operaciones y reportes para " +
                                "organizaciones religiosas multi-sede.")
                        .version("v1")
                        .contact(new Contact()
                                .name("DOXAPP")
                                .email("doxa.app.ts@gmail.com")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Pegar acá el token JWT devuelto por POST /auth/login " +
                                        "(sin el prefijo \"Bearer \", Swagger lo agrega solo).")));
    }
}
