package pe.dcs.app.features.user.org_user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.user.org_user.request.OrgUserCreateRequest;
import pe.dcs.app.features.user.org_user.request.OrgUserSearchRequest;
import pe.dcs.app.features.user.org_user.request.OrgUserUpdateRequest;
import pe.dcs.app.features.user.org_user.response.OrgUserResponse;
import pe.dcs.app.features.user.org_user.response.OrgUserSearchRowResponse;
import pe.dcs.app.features.user.org_user.service.OrgUserService;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/**
 * Crear/editar/listar personas (congregantes) con rol ORG_USER,
 * dentro de la organización/sede del contexto de quien hace la
 * petición. Reemplaza el flujo que antes quedaba pendiente en
 * access-user (ahí solo se asignan módulos a personas que ya
 * existen; acá es donde se crean).
 */
@RestController
@RequestMapping("/api/v1/org-user")
@RequiredArgsConstructor
public class OrgUserController {

    private final OrgUserService service;

    @PostMapping("/search")
    public ApiResponse<PageResponse<OrgUserSearchRowResponse>> search(
            @RequestBody OrgUserSearchRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.personasObtenidasCorrectamente",
                service.search(request)
        );
    }

    @PostMapping("/create")
    public ApiResponse<OrgUserResponse> create(
            @Valid @RequestBody OrgUserCreateRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.personaCreadaCorrectamente",
                service.create(request)
        );
    }

    @PutMapping("/update/{id}")
    public ApiResponse<OrgUserResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody OrgUserUpdateRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.personaActualizadaCorrectamente",
                service.update(id, request)
        );
    }

    @GetMapping("/get/{id}")
    public ApiResponse<OrgUserResponse> getById(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.personaEncontrada",
                service.getById(id)
        );
    }

}
