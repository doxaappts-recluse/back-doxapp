package pe.dcs.app.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Lee "Authorization: Bearer", valida el JWT y publica la autenticación. Si es inválido/vencido deja que el entry point responda 401. */
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String EXPIRED_ATTR = "doxapp.jwt.expired";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            JwtService.Parsed parsed = jwtService.parse(header.substring(7).trim());
            if (parsed.valid()) {
                SecurityContextHolder.getContext().setAuthentication(new JwtAuthentication(parsed.actor()));
            } else if (parsed.expired()) {
                request.setAttribute(EXPIRED_ATTR, Boolean.TRUE);
            }
        }
        chain.doFilter(request, response);
    }
}
