package pe.dcs.app.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import pe.dcs.app.security.jwt.JwtAuthFilter;
import pe.dcs.app.security.jwt.JwtEntryPoint;
import pe.dcs.app.security.jwt.JwtService;

import java.util.Arrays;
import java.util.List;

/**
 * Seguridad HTTP (stateless, JWT). Autorización por tipo de token: solo ACCESS abre la API; los intermedios
 * (PRE_AUTH, SETUP, CONTEXT) solo alcanzan los endpoints de su paso. El permiso por módulo lo aplica
 * {@code @ModuleAccess} (siempre en el back; el front solo lo refleja).
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    static final String API = "/api/v1";

    private final JwtService jwtService;
    private final JwtEntryPoint entryPoint;

    @Value("${app.cors.allowed-origins:http://localhost:4200,http://127.0.0.1:4200}")
    private String allowedOrigins;

    /** BCrypt con factor 12 (spec 00 §5: BCrypt ≥ 12). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(c -> c.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(entryPoint))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // públicos: login, refresh/logout (autenticados por el propio refresh token), recuperación, invitación
                        .requestMatchers(HttpMethod.POST,
                                API + "/auth/login",
                                API + "/auth/platform/login",
                                API + "/auth/o/*/login",
                                API + "/auth/refresh",
                                API + "/auth/logout",
                                API + "/auth/password/forgot",
                                API + "/auth/password/reset",
                                API + "/auth/invite/accept").permitAll()
                        .requestMatchers(API + "/public/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll()
                        // pasos intermedios
                        .requestMatchers(HttpMethod.POST, API + "/auth/mfa/verify").hasAuthority("SCOPE_PRE_AUTH")
                        .requestMatchers(HttpMethod.POST, API + "/auth/context")
                        .hasAnyAuthority("SCOPE_CONTEXT", "SCOPE_ACCESS")
                        .requestMatchers(HttpMethod.POST,
                                API + "/auth/mfa/setup", API + "/auth/mfa/enable", API + "/auth/password/change")
                        .hasAnyAuthority("SCOPE_SETUP", "SCOPE_ACCESS")
                        // todo lo demás: solo tokens de acceso completos
                        .requestMatchers(API + "/**").hasAuthority("SCOPE_ACCESS")
                        .anyRequest().denyAll())
                .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*")); // sin cookies (allowCredentials=false): el Bearer va en Authorization
        cfg.setExposedHeaders(List.of("X-Trace-Id", "Content-Disposition", "X-Export-Rows"));
        cfg.setAllowCredentials(false);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
