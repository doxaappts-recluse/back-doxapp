package pe.dcs.app.features.organization.service;

import java.util.Arrays;
import java.util.Optional;

/** Archivos de marca que puede subir una organización (el valor de {@code path} es el que viaja en la URL). */
public enum BrandingKind {
    LOGO_LIGHT("logo-light"),
    LOGO_DARK("logo-dark"),
    FAVICON("favicon"),
    LOGIN_BACKGROUND("login-background");

    public final String path;

    BrandingKind(String path) {
        this.path = path;
    }

    public static Optional<BrandingKind> fromPath(String path) {
        return Arrays.stream(values()).filter(k -> k.path.equals(path)).findFirst();
    }
}
