package pe.dcs.app.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import pe.dcs.app.security.jwt.JwtEntryPoint;
import pe.dcs.app.security.jwt.JwtProvider;
import pe.dcs.app.security.jwt.JwtTokenFilter;
import pe.dcs.app.security.service.credentials.CredentialDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtEntryPoint unauthorizedHandler;
    private final JwtProvider jwtProvider;
    private final CredentialDetailsService userDetailsService;

    public SecurityConfig(JwtEntryPoint unauthorizedHandler, JwtProvider jwtProvider, CredentialDetailsService userDetailsService) {
        this.unauthorizedHandler = unauthorizedHandler;
        this.jwtProvider = jwtProvider;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public JwtTokenFilter jwtTokenFilter() {
        return new JwtTokenFilter(jwtProvider, userDetailsService);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // Usa BCrypt para codificar contraseñas
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Configura CORS
                .csrf().disable() // Deshabilita CSRF (es común en APIs REST, si usas formularios lo habilitarías)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()  // Permite acceso a rutas de autenticación sin necesidad de login
                        .requestMatchers("/api/**").authenticated()  // Rutas de la API deben estar autenticadas
                        .anyRequest().authenticated()  // Cualquier otra petición también debe estar autenticada
                )
                .headers().frameOptions().sameOrigin() // Protección contra clickjacking
                .and()
                .addFilterBefore(jwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);  // Registra el filtro JWT antes de la autenticación estándar

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:4200",
                "https://doxapp-2e3c7.web.app/"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")); // Métodos HTTP permitidos
        config.setAllowedHeaders(List.of("*")); // Permite todos los encabezados
        config.setAllowCredentials(true); // Permite credenciales (como cookies y encabezados de autorización)

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config); // Aplica la configuración de CORS a todas las rutas
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http, PasswordEncoder passwordEncoder, CredentialDetailsService userDetailsService) throws Exception {
        return http.getSharedObject(AuthenticationManagerBuilder.class)
                .userDetailsService(userDetailsService)
                .passwordEncoder(passwordEncoder)
                .and()
                .build();
    }
}