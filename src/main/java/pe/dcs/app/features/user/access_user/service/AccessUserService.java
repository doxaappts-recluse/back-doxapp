package pe.dcs.app.features.user.access_user.service;

import pe.dcs.app.features.user.access_user.request.AccessUserListRequest;
import pe.dcs.app.features.user.access_user.request.AccessUserUpdateRequest;
import pe.dcs.app.features.user.access_user.response.AccessUserConfigResponse;
import pe.dcs.app.features.user.access_user.response.AccessUserResponse;
import pe.dcs.app.features.user.shared.UserChangePasswordRequest;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/**
 * El usuario (persona + credencial + UserAccess ORG_USER) se
 * crea en otro flujo. Este feature solo gestiona qué módulos
 * y permisos por módulo tiene habilitados.
 *
 * Todos los "id" de esta interfaz son el id del ACCESO
 * (UserAccess), no el de la persona: una persona puede tener
 * varios accesos ORG_USER, uno por sede.
 */
public interface AccessUserService {

    PageResponse<AccessUserResponse> search(AccessUserListRequest request);

    AccessUserConfigResponse getById(UUID accessId);

    void update(UUID accessId, AccessUserUpdateRequest request);

    void enable(UUID accessId);

    void disable(UUID accessId);

    void changePassword(UUID accessId, UserChangePasswordRequest request);

}
