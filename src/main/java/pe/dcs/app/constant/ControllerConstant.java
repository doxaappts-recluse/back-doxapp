package pe.dcs.app.constant;

public class ControllerConstant {

    public static final String VALIDACION_AUTHORIZATION_HEADER = "Validando Header: Authorization";
    public static final String SIN_AUTHORIZATION_HEADER = "Header Authorization no ingresado";
    public static final String SIN_AUTHORIZATION_HEADER_MESSAGE = "Se requiere ingresar el Header Authorization.";
    public static final String CON_AUTHORIZATION_HEADER = "Header Authorization ingresado: {}";

    public static final String FORMAT_REQUEST = "Formateando Request";

    public static final String INICIO_VALIDACION = "Inicio de Validación";
    public static final String INICIO_BUSQUEDA_CONGREGANTES = "Iniciando la Búsqueda de Congregantes";
    public static final String TOTAL_REGISTROS = "Total de Registros: {}";

    public static final String PARAM_SORT_APELLIDO = "apellido";
    public static final String PARAM_SORT_NOMBRE = "nombre";

    public static final String VALIDACION_CAMPOS_DUPLICIDAD_CONGREGANTE = "Congregante con Apellido(s): {} y Nombre(s): {} ya existente";
    public static final String VALIDACION_CAMPOS_SIN_DUPLICIDAD_CONGREGANTE = "Congregante con Apellido(s): {} y Nombre(s): {} no existente";

    public static final String CREDENCIALES_INVALIDAS = "Credenciales inválidas";
    public static final String ERROR_SERVER = "Error interno del Servidor";

    private ControllerConstant() {
    }
}
