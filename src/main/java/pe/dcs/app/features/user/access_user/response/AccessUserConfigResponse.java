package pe.dcs.app.features.user.access_user.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Detalle para el formulario de creación/edición:
 * datos básicos del usuario + módulos hijos disponibles
 * del contrato activo, cada uno con su estado "assigned"
 * y los permisos disponibles con su propio "assigned".
 *
 * En creación (sin persona todavía), "assigned" siempre
 * viene en false: es simplemente el catálogo disponible.
 *
 * "id" es el id del ACCESO (UserAccess) que se está editando.
 */
@Getter
@Setter
public class AccessUserConfigResponse {

    private UUID id;

    private UUID personId;

    private String name;
    private String lastname;
    private String dni;
    private String username;

    private UUID branchId;
    private String branchName;

    private List<AccessUserModuleConfigResponse> modules =
            new ArrayList<>();

}
