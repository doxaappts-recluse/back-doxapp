package pe.dcs.app.util.constant;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class GeneralConstant {

    public static final String ZONA_LOCAL = "America/Lima";
    public static final ZoneId ID_ZONA_LOCAL = ZoneId.of(ZONA_LOCAL);

    public static final String PATTERN_FECHA_COMPLETA_GUION = "dd-MM-yyyy HH:mm:ss";
    public static final String PATTERN_FECHA_SLASH = "dd/MM/yyyy";

    public static final String ROL_ADMIN = "ROLE_ADMIN";
    public static final String ROL_ENCARGADO = "ROLE_ENCARGADO";

    public static final DateTimeFormatter FORMATO_FECHA_ARCHIVO =
            DateTimeFormatter.ofPattern("dd-MM-yyyy_HH-mm-ss");

    public static String fechaArchivo() {
        return Instant.now()
                .atZone(ID_ZONA_LOCAL)
                .format(FORMATO_FECHA_ARCHIVO);
    }

    public static final String TIPO_EXTENSION_EXCEL =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public static final String EXTENSION_EXCEL = ".xlsx";

    private GeneralConstant() {
    }
}
