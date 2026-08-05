package pe.dcs.app.features.familygroup.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.familygroup.request.FamilyGroupFormRequest;
import pe.dcs.app.features.familygroup.request.FamilyGroupSearchRequest;
import pe.dcs.app.features.familygroup.request.FamilyMemberFormRequest;
import pe.dcs.app.features.familygroup.response.FamilyGroupDetailResponse;
import pe.dcs.app.features.familygroup.response.FamilyGroupPersonSearchResponse;
import pe.dcs.app.features.familygroup.response.FamilyGroupSearchRowResponse;
import pe.dcs.app.features.familygroup.response.FamilyMemberResponse;
import pe.dcs.app.features.familygroup.service.FamilyGroupService;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

/**
 * Grupo Familiar: listado, detalle, búsqueda de persona por DNI (jefe
 * de hogar o miembro), crear/editar y gestión de miembros.
 */
@RestController
@RequestMapping("/api/v1/family-group")
@RequiredArgsConstructor
public class FamilyGroupController {

    private final FamilyGroupService service;

    @PostMapping("/search")
    public ApiResponse<PageResponse<FamilyGroupSearchRowResponse>> search(
            @RequestBody FamilyGroupSearchRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.gruposFamiliaresObtenidosCorrectamente",
                service.search(request)
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<FamilyGroupDetailResponse> getById(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.grupoFamiliarObtenidoCorrectamente",
                service.getById(id)
        );
    }

    @GetMapping("/find-by-dni")
    public ApiResponse<FamilyGroupPersonSearchResponse> findPersonByDni(
            @RequestParam String dni
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.personaEncontradaCorrectamente",
                service.findPersonByDni(dni)
        );
    }

    @PostMapping("/create")
    public ApiResponse<UUID> create(
            @Valid @RequestBody FamilyGroupFormRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.grupoFamiliarRegistradoCorrectamente",
                service.create(request)
        );
    }

    @PutMapping("/update/{id}")
    public ApiResponse<String> update(
            @PathVariable UUID id,
            @Valid @RequestBody FamilyGroupFormRequest request
    ) {

        service.update(id, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.grupoFamiliarActualizadoCorrectamente",
                "OK"
        );
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<FamilyMemberResponse>> listMembers(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.miembrosObtenidosCorrectamente",
                service.listMembers(id)
        );
    }

    @PostMapping("/{id}/members")
    public ApiResponse<String> addMember(
            @PathVariable UUID id,
            @Valid @RequestBody FamilyMemberFormRequest request
    ) {

        service.addMember(id, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.miembroAgregadoCorrectamente",
                "OK"
        );
    }

    @PutMapping("/{id}/members/{memberId}")
    public ApiResponse<String> updateMemberRole(
            @PathVariable UUID id,
            @PathVariable UUID memberId,
            @Valid @RequestBody FamilyMemberFormRequest request
    ) {

        service.updateMemberRole(id, memberId, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.miembroActualizadoCorrectamente",
                "OK"
        );
    }

    @DeleteMapping("/{id}/members/{memberId}")
    public ApiResponse<String> removeMember(
            @PathVariable UUID id,
            @PathVariable UUID memberId
    ) {

        service.removeMember(id, memberId);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.miembroRemovidoCorrectamente",
                "OK"
        );
    }
}
