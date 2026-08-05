package pe.dcs.app.features.user.access_user.request;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * El usuario (persona + credencial) ya existe, creado por
 * otro flujo. Acá solo se agregan/quitan módulos y permisos
 * por módulo: es un reemplazo completo de la asignación actual.
 */
@Getter
@Setter
public class AccessUserUpdateRequest {

    @Valid
    private List<AccessUserModuleRequest> modules = new ArrayList<>();

}
