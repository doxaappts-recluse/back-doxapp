package pe.dcs.app.security.payload;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JwtTimesResponse {

    //Atributos
    private String emision;
    private String expiracion;

    //Constructores
    public JwtTimesResponse(String emision, String expiracion) {
        this.emision = emision;
        this.expiracion = expiracion;
    }
}
