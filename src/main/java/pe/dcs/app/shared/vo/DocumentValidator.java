package pe.dcs.app.shared.vo;

import java.util.regex.Pattern;

/**
 * Núcleo 01 §1 · Validación única de documentos (prohibido duplicarla en módulos).
 * DNI 8 dígitos · CE 9–12 alfanuméricos · PASSPORT 6–12 alfanuméricos · RUC 11 dígitos con dígito verificador.
 */
public final class DocumentValidator {

    private static final Pattern DNI = Pattern.compile("^\\d{8}$");
    private static final Pattern CE = Pattern.compile("^[A-Za-z0-9]{9,12}$");
    private static final Pattern PASSPORT = Pattern.compile("^[A-Za-z0-9]{6,12}$");
    private static final Pattern RUC = Pattern.compile("^\\d{11}$");
    private static final int[] RUC_WEIGHTS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    private DocumentValidator() {
    }

    public static boolean isValid(DocumentType type, String number) {
        if (type == null || number == null) {
            return false;
        }
        return switch (type) {
            case DNI -> DNI.matcher(number).matches();
            case CE -> CE.matcher(number).matches();
            case PASSPORT -> PASSPORT.matcher(number).matches();
            case RUC -> RUC.matcher(number).matches() && rucCheckDigitOk(number);
        };
    }

    /** Normaliza para comparar/guardar: sin espacios y en mayúsculas. */
    public static String normalize(String number) {
        return number == null ? null : number.trim().toUpperCase();
    }

    private static boolean rucCheckDigitOk(String ruc) {
        String prefix = ruc.substring(0, 2);
        if (!(prefix.equals("10") || prefix.equals("15") || prefix.equals("16")
                || prefix.equals("17") || prefix.equals("20"))) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            sum += (ruc.charAt(i) - '0') * RUC_WEIGHTS[i];
        }
        int check = 11 - (sum % 11);
        if (check == 10) {
            check = 0;
        } else if (check == 11) {
            check = 1;
        }
        return check == (ruc.charAt(10) - '0');
    }
}
