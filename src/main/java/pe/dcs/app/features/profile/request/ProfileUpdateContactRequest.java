package pe.dcs.app.features.profile.request;

import lombok.Getter;
import lombok.Setter;

/**
 * Autoedición de datos de contacto del usuario autenticado.
 * Deliberadamente acotado a phone/address: nombre, DNI, sexo,
 * fecha de nacimiento, estado civil e hijos quedan de solo
 * lectura acá — los gestiona el admin desde la ficha de la
 * persona (org-user / access-user / system-user).
 */
@Getter
@Setter
public class ProfileUpdateContactRequest {

    private String phone;

    private String address;
}
