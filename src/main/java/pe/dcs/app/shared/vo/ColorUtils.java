package pe.dcs.app.shared.vo;

import java.util.regex.Pattern;

/** Colores de marca: formato #RRGGBB y contraste WCAG (M02 [V10] [V12]). */
public final class ColorUtils {

    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    /** Contraste mínimo del texto blanco sobre el color primario (WCAG AA, texto normal). */
    public static final double MIN_CONTRAST = 4.5;

    private ColorUtils() {
    }

    public static boolean isHex(String c) {
        return c != null && HEX.matcher(c).matches();
    }

    public static String normalize(String c) {
        return c == null ? null : c.trim().toUpperCase();
    }

    /** Contraste del texto blanco sobre {@code hex}: (1.05) / (L + 0.05). */
    public static double contrastWithWhite(String hex) {
        return 1.05 / (luminance(hex) + 0.05);
    }

    /** {@code hex} si ya cumple el contraste; si no, el mismo tono oscurecido lo mínimo necesario. */
    public static String accessible(String hex) {
        String c = hex;
        for (int i = 0; i < 60 && contrastWithWhite(c) < MIN_CONTRAST; i++) {
            c = mixWithBlack(c, 0.04);
        }
        return c;
    }

    private static String mixWithBlack(String hex, double amount) {
        int r = (int) Math.round(Integer.parseInt(hex.substring(1, 3), 16) * (1 - amount));
        int g = (int) Math.round(Integer.parseInt(hex.substring(3, 5), 16) * (1 - amount));
        int b = (int) Math.round(Integer.parseInt(hex.substring(5, 7), 16) * (1 - amount));
        return String.format("#%02X%02X%02X", r, g, b);
    }

    private static double luminance(String hex) {
        double r = channel(Integer.parseInt(hex.substring(1, 3), 16));
        double g = channel(Integer.parseInt(hex.substring(3, 5), 16));
        double b = channel(Integer.parseInt(hex.substring(5, 7), 16));
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }
}
