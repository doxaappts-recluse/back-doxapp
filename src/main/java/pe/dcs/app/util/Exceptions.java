package pe.dcs.app.util;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class Exceptions extends RuntimeException {

    private final HttpStatus status;

    public Exceptions(
            String mensaje,
            HttpStatus status
    ) {
        super(mensaje);
        this.status = status;
    }
}