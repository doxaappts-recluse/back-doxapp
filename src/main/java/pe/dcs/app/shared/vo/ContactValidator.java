package pe.dcs.app.shared.vo;

import java.util.regex.Pattern;

/** Núcleo 01 §1 · Contact: teléfono E.164 y correo RFC (subconjunto práctico). */
public final class ContactValidator {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");

    private ContactValidator() {
    }

    public static boolean isValidPhone(String phone) {
        return phone != null && E164.matcher(phone).matches();
    }

    public static boolean isValidEmail(String email) {
        return email != null && email.length() <= 160 && EMAIL.matcher(email).matches();
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
