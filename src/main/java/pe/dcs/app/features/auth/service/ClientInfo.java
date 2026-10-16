package pe.dcs.app.features.auth.service;

import jakarta.servlet.http.HttpServletRequest;

/** IP y agente de usuario del cliente (para eventos de seguridad y sesiones). */
public record ClientInfo(String ip, String userAgent) {

    public static ClientInfo from(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return new ClientInfo(request.getRemoteAddr(), ua == null ? null : ua.substring(0, Math.min(ua.length(), 255)));
    }

    public static ClientInfo unknown() {
        return new ClientInfo("0.0.0.0", null);
    }
}
