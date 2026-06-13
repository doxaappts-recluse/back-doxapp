package pe.dcs.app.features.rol;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.rol.response.RoleResponse;
import pe.dcs.app.features.rol.service.RolService;
import pe.dcs.app.util.ApiResponse;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RolService rolService;

    @GetMapping("/getAll")
    public ApiResponse<List<RoleResponse>> getAll() {

        return new ApiResponse<List<RoleResponse>>(
                200,
                "Roles obtenidos correctamente",
                rolService.getAll()
        );
    }

    // =========================================================
    // GET SYSTEM ROLES
    // =========================================================

    @GetMapping("/system")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getSystemRoles() {

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Roles del sistema obtenidos correctamente",
                        rolService.getSystemRoles()
                )
        );
    }


    // =========================================================
    // GET ORGANIZATION ROLES
    // =========================================================

    @GetMapping("/organization")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getOrganizationRoles() {

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Roles de organización obtenidos correctamente",
                        rolService.getOrganizationRoles()
                )
        );
    }

}