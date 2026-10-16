package pe.dcs.app.features.integration.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.integration.domain.IntegrationProvider;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Prueba real de un servidor SMTP: conecta, lee el saludo 220, EHLO, TLS si corresponde y, con usuario y contraseña,
 * AUTH LOGIN (235). No envía correos. Por seguridad no conecta a direcciones locales o privadas salvo en desarrollo
 * ({@code app.integrations.allow-private-hosts}) para evitar que una organización sondee la red interna.
 */
@Component
public class SmtpTester implements IntegrationTester {

    private static final int TIMEOUT_MS = 6000;

    private final boolean allowPrivate;

    public SmtpTester(@Value("${app.integrations.allow-private-hosts:${app.dev.reset-on-start:false}}") boolean allowPrivate) {
        this.allowPrivate = allowPrivate;
    }

    @Override
    public boolean supports(IntegrationProvider provider) {
        return provider == IntegrationProvider.EMAIL_SMTP;
    }

    @Override
    public TestResult test(Map<String, Object> settings, Map<String, String> secrets) {
        String host = str(settings.get("host"));
        int port;
        try {
            port = Integer.parseInt(str(settings.get("port")));
        } catch (NumberFormatException e) {
            return TestResult.fail("puerto inválido");
        }
        String security = str(settings.get("security"));
        try {
            InetAddress addr = InetAddress.getByName(host);
            if (!allowPrivate && (addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress())) {
                return TestResult.fail("dirección no permitida");
            }
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(addr, port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
            try {
                if ("SSL".equals(security)) {
                    socket = tls(socket, host, port);
                }
                Session s = new Session(socket);
                String greet = s.readReply();
                if (!greet.startsWith("220")) {
                    return TestResult.fail("saludo inesperado del servidor");
                }
                String ehlo = s.command("EHLO doxapp.local");
                if (!ehlo.startsWith("250")) {
                    return TestResult.fail("el servidor rechazó EHLO");
                }
                if ("STARTTLS".equals(security)) {
                    if (!ehlo.toUpperCase().contains("STARTTLS")) {
                        return TestResult.fail("el servidor no ofrece STARTTLS");
                    }
                    if (!s.command("STARTTLS").startsWith("220")) {
                        return TestResult.fail("STARTTLS rechazado");
                    }
                    s = new Session(tls(socket, host, port));
                    ehlo = s.command("EHLO doxapp.local");
                }
                String user = str(settings.get("username"));
                String pass = secrets.getOrDefault("password", "");
                if (!user.isEmpty() && !pass.isEmpty()) {
                    if (!s.command("AUTH LOGIN").startsWith("334")) {
                        return TestResult.fail("el servidor no admite AUTH LOGIN");
                    }
                    if (!s.command(b64(user)).startsWith("334") || !s.command(b64(pass)).startsWith("235")) {
                        return TestResult.fail("usuario o contraseña rechazados");
                    }
                }
                s.command("QUIT");
                return TestResult.success();
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // nada
                }
            }
        } catch (Exception e) {
            return TestResult.fail(e.getClass().getSimpleName().replace("Exception", "").toLowerCase() + ": no se pudo conectar");
        }
    }

    private static Socket tls(Socket plain, String host, int port) throws IOException {
        SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(plain, host, port, true);
        ssl.setSoTimeout(TIMEOUT_MS);
        ssl.startHandshake();
        return ssl;
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private static final class Session {
        private final BufferedReader in;
        private final OutputStream out;

        Session(Socket s) throws IOException {
            this.in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            this.out = s.getOutputStream();
        }

        String command(String line) throws IOException {
            out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            return readReply();
        }

        /** Lee una respuesta (posiblemente de varias líneas "250-…") y devuelve todo el texto. */
        String readReply() throws IOException {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line).append('\n');
                if (line.length() < 4 || line.charAt(3) != '-') {
                    break;
                }
            }
            return sb.toString();
        }
    }
}
