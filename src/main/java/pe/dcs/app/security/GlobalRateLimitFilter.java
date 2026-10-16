package pe.dcs.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.dcs.app.shared.web.ApiError;
import pe.dcs.app.shared.web.TraceIdFilter;
import pe.dcs.app.util.MessageSourceHolder;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Punto #11 de la auditoría de seguridad (2026-10-09): límite general por IP sobre TODA la API, además de los
 * límites específicos que ya existían por módulo (login, visitantes, eventos, portal, certificados). Esos
 * límites puntuales siguen activos e intactos — este filtro es solo una segunda capa, más amplia y más laxa,
 * para que ninguna ruta se quede sin ningún límite. Igual que {@code LoginRateLimiter}: ventana deslizante en
 * memoria, por proceso; en despliegue con varias instancias debe moverse a un almacén compartido (Redis).
 *
 * <p>Corre antes de {@code JwtAuthFilter} (ver {@link SecurityConfig}) para frenar el abuso lo antes posible,
 * incluso contra rutas públicas o inexistentes. Nunca cuenta `OPTIONS` (preflight de CORS, sin costo real).
 */
@Component
public class GlobalRateLimitFilter extends OncePerRequestFilter {

    private final int maxRequests;
    private final Duration window;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    public GlobalRateLimitFilter(@Value("${app.security.global-rate-limit.requests:300}") int maxRequests,
                                  @Value("${app.security.global-rate-limit.window-seconds:60}") long windowSeconds,
                                  Clock clock, ObjectMapper objectMapper) {
        this.maxRequests = maxRequests;
        this.window = Duration.ofSeconds(windowSeconds);
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || maxRequests <= 0) {
            chain.doFilter(request, response);
            return;
        }
        String ip = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        if (!allow(ip)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getOutputStream(), new ApiError(
                    HttpStatus.TOO_MANY_REQUESTS.value(), "error.common.globalRateLimited",
                    MessageSourceHolder.resolve("error.common.globalRateLimited"), null,
                    TraceIdFilter.current(), Instant.now()));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean allow(String ip) {
        Deque<Instant> q = hits.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (q) {
            Instant limit = clock.instant().minus(window);
            while (!q.isEmpty() && q.peekFirst().isBefore(limit)) {
                q.removeFirst();
            }
            if (q.size() >= maxRequests) {
                return false;
            }
            q.addLast(clock.instant());
            return true;
        }
    }
}
