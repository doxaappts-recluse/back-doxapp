package pe.dcs.app.features.auth.dto;

import java.util.List;

/** Ítem de menú dirigido por el servidor: solo módulos donde el actor tiene V. {@code route} es relativa al prefijo del nivel; el nombre viaja en es y en para que el front cambie de idioma sin volver a pedir el menú. */
public record MenuItem(String code, String nameEs, String nameEn, String route, String icon, List<String> actions) {
}
