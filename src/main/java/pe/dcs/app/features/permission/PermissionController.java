package pe.dcs.app.features.permission;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.permission.response.PermissionResponse;
import pe.dcs.app.features.permission.service.PermissionService;
import pe.dcs.app.util.ApiResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping("/list")
    public ApiResponse<List<PermissionResponse>>
    findAll() {

        return new ApiResponse<>(
                200,
                "Permisos cargados correctamente",
                permissionService.findAll()
        );
    }
}