package pe.dcs.app.util;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.util.stream.Collectors;


@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Excepciones de autenticación de Spring Security (login).
     * Ojo: si se dejan caer al handler genérico de RuntimeException,
     * ex.getMessage() devuelve el texto INTERNO de Spring Security
     * ("User is disabled", "Bad credentials", etc.), que viene de su
     * propio bundle de mensajes (spring-security-core), no del
     * MessageSource de esta app — por eso salía en inglés aunque el
     * resto de la app ya respondía en el idioma del header
     * Accept-Language. Acá se traducen explícitamente a nuestras
     * propias claves para que sigan el mismo idioma que todo lo demás.
     */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<Object>> handleDisabled(DisabledException ex) {

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(
                        HttpStatus.UNAUTHORIZED.value(),
                        MessageSourceHolder.resolve("error.usuarioDeshabilitadoLogin"),
                        null
                ));
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiResponse<Object>> handleLocked(LockedException ex) {

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(
                        HttpStatus.UNAUTHORIZED.value(),
                        MessageSourceHolder.resolve("error.cuentaBloqueada"),
                        null
                ));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadCredentials(BadCredentialsException ex) {

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(
                        HttpStatus.UNAUTHORIZED.value(),
                        MessageSourceHolder.resolve("error.credencialesInvalidas"),
                        null
                ));
    }

    /** Catch-all para cualquier otra AuthenticationException no cubierta arriba. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthentication(AuthenticationException ex) {

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(
                        HttpStatus.UNAUTHORIZED.value(),
                        MessageSourceHolder.resolve("error.credencialesInvalidas"),
                        null
                ));
    }

    @ExceptionHandler(Exceptions.class)
    public ResponseEntity<ApiResponse<Object>> handleApiException(Exceptions ex) {

        return ResponseEntity
                .status(ex.getStatus())
                .body(new ApiResponse<>(
                        ex.getStatus().value(),
                        ex.getMessage(),
                        null
                ));
    }

    /**
     * Bean Validation (@Valid en @RequestBody). Los mensajes de las
     * anotaciones (@NotBlank(message = "{error.xxx}"), etc.) ya vienen
     * resueltos en el idioma correcto acá: Spring Boot conecta
     * automáticamente el MessageSource de la app (messages_es/en.properties,
     * ver MessageSourceHolder/WebI18nConfig) al validador cuando el
     * message usa la sintaxis "{clave}", así que no hace falta pasar
     * por MessageSourceHolder de nuevo. Se listan todos los campos con
     * error (no solo el primero) para que el usuario los corrija de una vez.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(" | "));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(
                        HttpStatus.BAD_REQUEST.value(),
                        message,
                        null
                ));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiResponse<Object>> handleIOException(IOException ex) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(
                        HttpStatus.BAD_REQUEST.value(),
                        MessageSourceHolder.resolve("error.errorAlProcesarArchivo"),
                        null
                ));
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ApiResponse<Object>> handleWebClient(WebClientResponseException ex) {

        return ResponseEntity
                .status(ex.getStatusCode())
                .body(new ApiResponse<>(
                        ex.getStatusCode().value(),
                        MessageSourceHolder.resolve("error.errorEnAlmacenamientoExterno"),
                        null
                ));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntime(RuntimeException ex) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(
                        HttpStatus.BAD_REQUEST.value(),
                        ex.getMessage(),
                        null
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception ex) {

        ex.printStackTrace();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        MessageSourceHolder.resolve("error.errorInternoServidor"),
                        null
                ));
    }
}