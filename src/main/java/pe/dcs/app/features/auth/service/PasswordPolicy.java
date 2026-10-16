package pe.dcs.app.features.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.dcs.app.util.Exceptions;

/** Política de contraseñas única (spec 00 §5): ≥ 10 caracteres, mayúscula, minúscula y número; distinta del usuario. */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 128;

    public boolean isValid(String password, String username) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return false;
        }
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        for (int i = 0; i < password.length(); i++) {
            char ch = password.charAt(i);
            upper |= Character.isUpperCase(ch);
            lower |= Character.isLowerCase(ch);
            digit |= Character.isDigit(ch);
        }
        if (!(upper && lower && digit)) {
            return false;
        }
        return username == null || !password.equalsIgnoreCase(username.trim());
    }

    /** Lanza 400 {@code error.auth.passwordWeak} si no cumple. */
    public void assertValid(String password, String username) {
        if (!isValid(password, username)) {
            throw new Exceptions("error.auth.passwordWeak", HttpStatus.BAD_REQUEST);
        }
    }
}
