package pe.dcs.app.features.auth.response;

import java.util.List;

public class JwtResponse {
    private String token;
    private String username;
    private String nombre;
    private String rol;
    private String emissionTime;
    private String expirationTime;

    public JwtResponse(String token, String username, String nombre, String rol, String emissionTime, String expirationTime) {
        this.token = token;
        this.username = username;
        this.nombre = nombre;
        this.rol = rol;
        this.emissionTime = emissionTime;
        this.expirationTime = expirationTime;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public String getEmissionTime() {
        return emissionTime;
    }

    public void setEmissionTime(String emissionTime) {
        this.emissionTime = emissionTime;
    }

    public String getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(String expirationTime) {
        this.expirationTime = expirationTime;
    }

}
