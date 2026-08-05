package pe.dcs.app.util;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.util.stream.Collectors;


@RestControllerAdvice
public class GlobalExceptionHandler {

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