package pe.dcs.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Punto #19 de la auditoría de seguridad (2026-10-09). Hoy el HTTPS se da por descontado porque Render ya
 * termina TLS para *.onrender.com (por eso el HSTS de {@link SecurityConfig} tiene sentido); esto es una
 * segunda capa, apagada por defecto ({@code app.security.force-https=false}), para el día en que la app se
 * despliegue detrás de otro proxy que sí necesite que el back mismo fuerce la redirección.
 *
 * <p>Como {@code server.forward-headers-strategy: framework} ya está activo (application.yml), Spring registra
 * su propio {@code ForwardedHeaderFilter} ANTES de la cadena de Spring Security: ese filtro lee
 * {@code X-Forwarded-Proto}, reescribe {@code request.getScheme()}/{@code isSecure()} con el valor real y
 * CONSUME la cabecera cruda (ya no llega aquí). Por eso este filtro mira {@code request.isSecure()} —ya resuelto
 * correctamente por detrás del proxy— en vez de leer la cabecera directamente.
 */
@Component
public class HttpsRedirectFilter extends OncePerRequestFilter {

    private final boolean enabled;

    public HttpsRedirectFilter(@Value("${app.security.force-https:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (enabled && !request.isSecure()) {
            String host = request.getHeader("Host");
            String target = "https://" + (host == null ? request.getServerName() : host) + request.getRequestURI()
                    + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
            response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            response.setHeader("Location", target);
            return;
        }
        chain.doFilter(request, response);
    }
}
