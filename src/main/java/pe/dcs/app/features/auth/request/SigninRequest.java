package pe.dcs.app.features.auth.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SigninRequest {

    //Atributos
    @NotEmpty(message = "El campo es requerido.")
    private String username;

    @NotEmpty(message = "El campo es requerido.")
    private String password;

    //Constructores

    public SigninRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
