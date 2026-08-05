package pe.dcs.app.features.profile.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Auto-cambio de contraseña del usuario autenticado (distinto del
 * reseteo administrativo de AccessUserServiceImpl/UserSystemServiceImpl,
 * que no pide la contraseña vigente porque lo hace un admin sobre
 * otra persona). Acá sí se exige currentPassword para confirmar
 * identidad antes de guardar la nueva.
 */
@Getter
@Setter
public class ProfileChangePasswordRequest {

    @NotBlank(message = "{error.passwordObligatorio}")
    private String currentPassword;

    @NotBlank(message = "{error.passwordObligatorio}")
    private String newPassword;
}
