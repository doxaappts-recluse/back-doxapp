package pe.dcs.app.features.user.access_user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.user.access_user.request.AccessUserListRequest;
import pe.dcs.app.features.user.access_user.request.AccessUserUpdateRequest;
import pe.dcs.app.features.user.access_user.response.AccessUserConfigResponse;
import pe.dcs.app.features.user.access_user.response.AccessUserResponse;
import pe.dcs.app.features.user.access_user.service.AccessUserService;
import pe.dcs.app.features.user.shared.UserChangePasswordRequest;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/**
 * Asignación de módulos/permisos a accesos ORG_USER que ya
 * existen (persona + credencial + UserAccess creados en otro
 * flujo). El {id} de cada endpoint es el id del ACCESO
 * (UserAccess), no el de la persona: una persona puede tener
 * varios accesos ORG_USER (uno por sede), cada uno gestionado
 * de forma independiente. Enable/disable/cambio de contraseña
 * afectan la Credential de la persona dueña del acceso (1:1
 * persona-credencial, no cambia por el multi-acceso).
 * Solo ORG_ADMIN / ORG_BRANCH_ADMIN (se valida en el service).
 */
@RestController
@RequestMapping("/api/v1/access-users")
@RequiredArgsConstructor
public class AccessUserController {

    private final AccessUserService service;

    @PostMapping("/search")
    public ApiResponse<PageResponse<AccessUserResponse>> search(
            @RequestBody AccessUserListRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.usuariosObtenidosCorrectamente",
                service.search(request)
        );
    }

    @GetMapping("/getById/{id}")
    public ApiResponse<AccessUserConfigResponse> findById(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.usuarioEncontrado",
                service.getById(id)
        );
    }

    @PutMapping("/update/{id}")
    public ApiResponse<String> update(
            @PathVariable UUID id,
            @Valid @RequestBody AccessUserUpdateRequest request
    ) {

        service.update(id, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.modulosActualizados",
                null
        );
    }

    @PatchMapping("/enable/{id}")
    public ApiResponse<String> enable(
            @PathVariable UUID id
    ) {

        service.enable(id);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.usuarioHabilitadoCorrectamente",
                "OK"
        );
    }

    @PatchMapping("/disable/{id}")
    public ApiResponse<String> disable(
            @PathVariable UUID id
    ) {

        service.disable(id);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.usuarioDeshabilitadoCorrectamente",
                "OK"
        );
    }

    @PatchMapping("/{id}/change-password")
    public ApiResponse<String> changePassword(
            @PathVariable UUID id,
            @Valid @RequestBody UserChangePasswordRequest request
    ) {

        service.changePassword(id, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.contrasenaActualizadaCorrectamente",
                "OK"
        );
    }

}
