package pe.dcs.app.features.user.shared;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserChangePasswordRequest {
    private String password;

    /**
     * Opcional. Solo lo usa Usuarios de Acceso (permite cambiar el
     * usuario junto con la contraseña); otros flujos que reusan
     * este DTO (p.ej. Usuarios del Sistema) simplemente lo ignoran
     * si no lo envían.
     */
    private String username;
}