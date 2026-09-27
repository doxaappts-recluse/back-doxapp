package pe.dcs.app.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import pe.dcs.app.shared.web.ApiError;
import pe.dcs.app.shared.web.TraceIdFilter;
import pe.dcs.app.util.MessageSourceHolder;

import java.io.IOException;
import java.time.Instant;

/** 401 (sin token / token inválido o vencido) y 403 (token de tipo no admitido en ese endpoint) con el cuerpo ApiError unificado. */
@Component
@RequiredArgsConstructor
public class JwtEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        boolean expired = request.getAttribute(JwtAuthFilter.EXPIRED_ATTR) != null;
        // El mensaje es el mismo (no se revela el motivo); el código distingue vencido de inválido para el front.
        String code = expired ? "error.auth.sessionExpired" : "error.auth.tokenInvalid";
        write(response, HttpStatus.UNAUTHORIZED, "error.auth.sessionExpired", code);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       org.springframework.security.access.AccessDeniedException ex) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "error.common.forbidden", "error.common.forbidden");
    }

    private void write(HttpServletResponse response, HttpStatus status, String messageKey, String code) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), new ApiError(
                status.value(), code, MessageSourceHolder.resolve(messageKey), null,
                TraceIdFilter.current(), Instant.now()));
    }
}
