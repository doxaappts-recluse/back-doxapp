package pe.dcs.app.constant;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class GeneralConstant {

    public static final String ZONA_LOCAL = "America/Lima";
    public static final ZoneId ID_ZONA_LOCAL = ZoneId.of(GeneralConstant.ZONA_LOCAL);
    public static final LocalDateTime FECHA_HORA_LOCAL = LocalDateTime.now().atZone(ID_ZONA_LOCAL).toLocalDateTime();
    public static final String PATTERN_FECHA_COMPLETA_GUION = "dd-MM-yyyy HH:mm:ss";
    public static final String PATTERN_FECHA_SLASH = "dd/MM/yyyy";

    public static final String ROL_ADMIN = "ROLE_ADMIN";
    public static final String ROL_ENCARGADO = "ROLE_ENCARGADO";

    public static final DateTimeFormatter FORMATO_FECHA_ARCHIVO = DateTimeFormatter.ofPattern("dd-MM-yyyy_HH:mm:ss");
    public static final String FECHA_ARCHIVO = GeneralConstant.FECHA_HORA_LOCAL.format(FORMATO_FECHA_ARCHIVO);
    public static final String TIPO_EXTENSION_EXCEL = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String EXTENSION_EXCEL = ".xlsx";
    public static final String NOMBRE_ARCHIVO_EXCEL = "Reporte_Congregantes_" + FECHA_ARCHIVO + EXTENSION_EXCEL;

    private GeneralConstant() {
    }
}
