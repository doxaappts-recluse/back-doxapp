package pe.dcs.app.util;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import pe.dcs.app.shared.web.ApiError;
import pe.dcs.app.shared.web.TraceIdFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Spec 00 §10 · errores unificados. El cuerpo es siempre {@link ApiError}; el mensaje se resuelve por
 * Accept-Language. Nunca se filtra el mensaje interno de una excepción inesperada: se devuelve un traceId.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                                  List<ApiError.FieldError> fieldErrors) {
        return ResponseEntity.status(status).body(new ApiError(
                status.value(), code, message, fieldErrors, TraceIdFilter.current(), Instant.now()));
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String key, Object... args) {
        return build(status, key, MessageSourceHolder.resolve(key, args), null);
    }

    private static String dataLabel() {
        return MessageSourceHolder.resolve("common.data");
    }

    /** Mensaje de campo: la anotación trae una clave i18n; {0} = nombre del campo, {1} = límite (@Size max). */
    private static ApiError.FieldError fieldError(org.springframework.validation.FieldError fe) {
        Object max = null;
        try {
            ConstraintViolation<?> v = fe.unwrap(ConstraintViolation.class);
            max = v.getConstraintDescriptor().getAttributes().get("max");
        } catch (RuntimeException ignored) {
            // no era una violación de Bean Validation
        }
        String key = fe.getDefaultMessage();
        return new ApiError.FieldError(fe.getField(), key,
                MessageSourceHolder.resolve(key, fe.getField(), max == null ? "" : max));
    }

    @ExceptionHandler(Exceptions.class)
    public ResponseEntity<ApiError> handleApiException(Exceptions ex) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), null);
    }

    /** Login: mensaje genérico, no revela si el usuario existe (spec 00 §5). */
    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "error.auth.invalidCredentials");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "error.common.forbidden");
    }

    /** Bean Validation en @RequestBody: 400 con la lista completa de campos. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fieldError(fe))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "error.common.invalid",
                MessageSourceHolder.resolve("error.common.invalid", dataLabel()), fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex) {
        List<ApiError.FieldError> fields = ex.getConstraintViolations().stream()
                .map((ConstraintViolation<?> v) -> new ApiError.FieldError(
                        v.getPropertyPath().toString(), v.getMessage(), MessageSourceHolder.resolve(v.getMessage(), v.getPropertyPath().toString(), "")))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "error.common.invalid",
                MessageSourceHolder.resolve("error.common.invalid", dataLabel()), fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "error.common.invalid", dataLabel());
    }

    /** Un envío multipart que supera el máximo del servidor (los adjuntos de soporte validan 10 MB en el servicio). */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "error.common.fileSize", "10 MB");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleConcurrent(OptimisticLockingFailureException ex) {
        return build(HttpStatus.CONFLICT, "error.common.concurrentUpdate");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad [{}]: {}", TraceIdFilter.current(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "error.common.duplicate", MessageSourceHolder.resolve("common.value"));
    }

    @ExceptionHandler({NoResourceFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(Exception ex) {
        return build(HttpStatus.NOT_FOUND, "error.common.notFound");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "error.common.invalid", dataLabel());
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiError> handleIO(IOException ex) {
        log.warn("IO [{}]: {}", TraceIdFilter.current(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "error.common.invalid", dataLabel());
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ApiError> handleWebClient(WebClientResponseException ex) {
        log.warn("Almacenamiento externo [{}]: {}", TraceIdFilter.current(), ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "error.common.server", TraceIdFilter.current());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Error inesperado [{}]", TraceIdFilter.current(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "error.common.server", TraceIdFilter.current());
    }
}
