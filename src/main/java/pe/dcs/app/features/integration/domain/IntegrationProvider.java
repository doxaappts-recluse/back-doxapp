package pe.dcs.app.features.integration.domain;

import java.util.List;

/** Proveedores de integración (M23). {@code kind}: solo una integración ACTIVE por (organización, tipo) [V9]. */
public enum IntegrationProvider {
    EMAIL_SMTP("EMAIL", List.of(
            Field.text("host", true, "Servidor SMTP", "SMTP server"),
            Field.number("port", true, "Puerto", "Port"),
            Field.select("security", true, "Seguridad", "Security", List.of("NONE", "STARTTLS", "SSL")),
            Field.text("username", false, "Usuario", "Username"),
            Field.secret("password", false, "Contraseña", "Password"),
            Field.email("fromEmail", true, "Correo remitente", "Sender email"),
            Field.text("fromName", false, "Nombre remitente", "Sender name"),
            Field.text("dkimSelector", false, "Selector DKIM", "DKIM selector"))),
    EMAIL_API("EMAIL", List.of(
            Field.text("apiProvider", true, "Proveedor", "Provider"),
            Field.email("fromEmail", true, "Correo remitente", "Sender email"),
            Field.text("fromName", false, "Nombre remitente", "Sender name"),
            Field.text("dkimSelector", false, "Selector DKIM", "DKIM selector"),
            Field.secret("apiKey", true, "Clave de API", "API key"))),
    SMS("SMS", List.of(
            Field.text("senderId", true, "Remitente", "Sender ID"),
            Field.secret("apiKey", true, "Clave de API", "API key"))),
    WHATSAPP("WHATSAPP", List.of(
            Field.text("phoneNumberId", true, "Identificador del número", "Phone number ID"),
            Field.secret("accessToken", true, "Token de acceso", "Access token"))),
    PAYMENT("PAYMENT", List.of(
            Field.text("gateway", true, "Pasarela", "Gateway"),
            Field.text("publicKey", false, "Clave pública", "Public key"),
            Field.secret("secretKey", true, "Clave secreta", "Secret key"))),
    STORAGE("STORAGE", List.of(
            Field.text("bucket", true, "Bucket", "Bucket"),
            Field.text("region", false, "Región", "Region"),
            Field.secret("accessKey", true, "Clave de acceso", "Access key"))),
    CALENDAR("CALENDAR", List.of(
            Field.text("calendarId", true, "Calendario", "Calendar ID"),
            Field.secret("serviceKey", true, "Clave de servicio", "Service key"))),
    VIDEO("VIDEO", List.of(
            Field.text("workspace", false, "Espacio de trabajo", "Workspace"),
            Field.secret("apiKey", true, "Clave de API", "API key")));

    private final String kind;
    private final List<Field> fields;

    IntegrationProvider(String kind, List<Field> fields) {
        this.kind = kind;
        this.fields = fields;
    }

    public String kind() {
        return kind;
    }

    public List<Field> fields() {
        return fields;
    }

    public boolean isEmail() {
        return "EMAIL".equals(kind);
    }

    /** type: text | number | email | select | secret. Los secretos se cifran y no se devuelven nunca [V8]. */
    public record Field(String name, String type, boolean required, String labelEs, String labelEn, List<String> options) {
        static Field text(String n, boolean r, String es, String en) {
            return new Field(n, "text", r, es, en, null);
        }

        static Field number(String n, boolean r, String es, String en) {
            return new Field(n, "number", r, es, en, null);
        }

        static Field email(String n, boolean r, String es, String en) {
            return new Field(n, "email", r, es, en, null);
        }

        static Field select(String n, boolean r, String es, String en, List<String> o) {
            return new Field(n, "select", r, es, en, o);
        }

        static Field secret(String n, boolean r, String es, String en) {
            return new Field(n, "secret", r, es, en, null);
        }

        public boolean secret() {
            return "secret".equals(type);
        }
    }
}
