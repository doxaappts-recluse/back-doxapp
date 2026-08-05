package pe.dcs.app.features.auth.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @NotBlank(message = "{error.usernameObligatorio}")
    private String username;

    @NotBlank(message = "{error.passwordObligatorio}")
    private String password;

}