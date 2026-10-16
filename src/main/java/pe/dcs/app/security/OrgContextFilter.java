package pe.dcs.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * [#4, auditoría 2026-10-09] Publica el {@code organizationId} del actor autenticado (si tiene uno) en
 * {@link OrgContextHolder} para que {@link RlsAwareDataSource} lo use al abrir cada conexión de esta petición.
 * Corre DESPUÉS de {@code JwtAuthFilter} (ver {@link SecurityConfig}) — necesita el actor ya puesto en el
 * {@code SecurityContext}.
 *
 * <p>Sin actor autenticado (rutas públicas), o actor STAFF sin acceso asistido activo (personal de plataforma
 * no pertenece a ninguna organización fuera de un grant — ver {@link AuthenticatedActor}), no fija nada: las
 * políticas RLS de person/user_access/fin_movement no dejan ver ninguna fila en ese caso, y la de credential
 * deja ver solo la credencial de plataforma propia (organization_id NULL) — exactamente lo que esos actores
 * deben poder tocar en esas 4 tablas.
 */
@Component
public class OrgContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthenticatedActor actor && actor.organizationId() != null) {
                OrgContextHolder.set(actor.organizationId().toString());
            }
            chain.doFilter(request, response);
        } finally {
            OrgContextHolder.clear();
        }
    }
}
