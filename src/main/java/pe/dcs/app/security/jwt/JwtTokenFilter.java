package pe.dcs.app.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.dcs.app.security.service.credentials.CredentialDetailsService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class JwtTokenFilter extends OncePerRequestFilter {

    private static final Logger filterLogger = LogManager.getLogger(JwtTokenFilter.class);

    final JwtProvider jwtProvider;

    final CredentialDetailsService userDetailsService;

    public JwtTokenFilter(JwtProvider jwtProvider, CredentialDetailsService userDetailsService) {
        this.jwtProvider = jwtProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req,
                                    @NonNull HttpServletResponse res,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            if (req.getRequestURI().startsWith("/auth/")) {
                filterChain.doFilter(req, res);
                return;
            }

            String jwt = parseJwt(req);

            if (jwt == null || !jwtProvider.validateJWT(jwt)) {
                sendUnauthorizedResponse(res, req.getServletPath(), "Token JWT inválido o faltante.");
                return;
            }

            String username = jwtProvider.getUsernameFromToken(jwt);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authToken);

            filterChain.doFilter(req, res);

        } catch (Exception ex) {
            filterLogger.error("Error en el filtro JWT: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
            sendUnauthorizedResponse(res, req.getServletPath(), "Error de autenticación.");
        }
    }

    private void sendUnauthorizedResponse(HttpServletResponse res, String path, String message) throws IOException {
        if (!res.isCommitted()) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);

            Map<String, Object> body = new HashMap<>();
            body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
            body.put("error", "No autorizado");
            body.put("message", message);
            body.put("path", path);

            new ObjectMapper().writeValue(res.getOutputStream(), body);
            res.getOutputStream().flush();
        }
    }

    private String parseJwt(HttpServletRequest request) {

        String header = request.getHeader("Authorization");

        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {

            return header.substring(7);
        }
        return null;
    }
}