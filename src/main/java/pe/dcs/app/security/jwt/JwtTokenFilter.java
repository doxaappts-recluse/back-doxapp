package pe.dcs.app.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.dcs.app.security.service.OrganizationContext;
import pe.dcs.app.security.service.credentials.CredentialDetailsImpl;
import pe.dcs.app.security.service.credentials.CredentialDetailsService;
import pe.dcs.app.util.constant.JwtConstant;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


@Component
@RequiredArgsConstructor
public class JwtTokenFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final CredentialDetailsService credentialDetailsService;
    private final OrganizationContext organizationContext;

    /*
     * Rutas públicas: deben coincidir con el permitAll() de
     * SecurityConfig. Este filtro corre ANTES de authorizeHttpRequests
     * (addFilterBefore), así que si no se excluyen acá explícitamente,
     * se rechazan con 401 sin importar lo que diga permitAll — el
     * permitAll de Spring Security nunca llega a evaluarse.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/auth/login")
                || path.equals("/swagger-ui.html")
                || path.startsWith("/swagger-ui/")
                || path.equals("/v3/api-docs")
                || path.startsWith("/v3/api-docs/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {

            String path = request.getRequestURI();

            String jwt =  parseJwt(request);

            if(jwt == null || !jwtProvider.validateJWT(jwt)){
                sendUnauthorizedResponse(
                        response,
                        request.getServletPath(),
                        "Token inválido."
                );
                return;
            }

            String tokenType = jwtProvider.getTokenType(jwt);

            /*
             * Flujos que todavía
             * no tienen contexto
             */
            boolean loginFlow = path.equals("/auth/context") || path.equals("/api/v1/context/available");

            String username = jwtProvider.getUsername(jwt);

            UUID organizationId = jwtProvider.getOrganizationId(jwt);

            UUID branchId = jwtProvider.getBranchId(jwt);

            CredentialDetailsImpl principal =
                    credentialDetailsService
                            .loadUserByUsername(
                                    username,
                                    organizationId,
                                    branchId
                            );

            /*
             * ===================================================
             * VALIDACION DE CONTEXTO
             * ===================================================
             *
             * SYSTEM ADMIN y SYSTEM SUPPORT
             * no necesitan contexto.
             *
             * Los demás roles necesitan
             * CONTEXT_TOKEN.
             */
            if(!loginFlow){

                boolean isSystem = principal.isSystemAdmin() || principal.isSystemSupport();

                if(!isSystem && !JwtConstant.CONTEXT_TOKEN.equals(tokenType)){
                    sendUnauthorizedResponse(
                            response,
                            request.getServletPath(),
                            "Debe seleccionar un contexto."
                    );
                    return;
                }

                /*
                 * Usuarios normales deben tener
                 * contexto de sede.
                 */
                if(!isSystem){
                    if(principal.getCurrentOrganizationId() == null){
                        sendUnauthorizedResponse(
                                response,
                                request.getServletPath(),
                                "Debe seleccionar una organización."
                        );
                        return;
                    }

                    if(principal.getCurrentBranchId() == null){
                        sendUnauthorizedResponse(
                                response,
                                request.getServletPath(),
                                "Debe seleccionar una sede."
                        );
                        return;
                    }
                }

            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            principal.getAuthorities()
                    );

            SecurityContextHolder
                    .getContext()
                    .setAuthentication(
                            authentication
                    );

            /*
             * Contexto actual (org/sede) para este hilo.
             * Lo consume AuthContext.getCurrentOrganizationId()/getCurrentBranchId().
             * Se limpia siempre en el finally: el hilo puede
             * reutilizarse para otra request (pool de Tomcat).
             */
            organizationContext.set(organizationId, branchId);

            try {
                filterChain.doFilter(request, response);
            } finally {
                organizationContext.clear();
            }

        }
        catch(Exception ex){

            SecurityContextHolder.clearContext();

            sendUnauthorizedResponse(
                    response,
                    request.getServletPath(),
                    ex.getMessage() != null
                            ? ex.getMessage()
                            : "No autorizado."
            );

        }

    }

    private String parseJwt(HttpServletRequest request){

        String header = request.getHeader("Authorization");

        if(StringUtils.hasText(header) && header.startsWith("Bearer ")){
            return header.substring(7);
        }

        return null;
    }

    private void sendUnauthorizedResponse(
            HttpServletResponse response,
            String path,
            String message
    ) throws IOException {

        if(response.isCommitted()){
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String,Object> body = new HashMap<>();

        body.put("status", 401);
        body.put("error", "Unauthorized");
        body.put("message", message);
        body.put("path", path);

        new ObjectMapper()
                .writeValue(
                        response.getOutputStream(),
                        body
                );
    }

}