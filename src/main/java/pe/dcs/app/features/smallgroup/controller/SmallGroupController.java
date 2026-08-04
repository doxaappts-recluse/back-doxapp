package pe.dcs.app.features.smallgroup.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.smallgroup.request.SmallGroupFormRequest;
import pe.dcs.app.features.smallgroup.request.SmallGroupMemberFormRequest;
import pe.dcs.app.features.smallgroup.request.SmallGroupSearchRequest;
import pe.dcs.app.features.smallgroup.response.SmallGroupDetailResponse;
import pe.dcs.app.features.smallgroup.response.SmallGroupMemberResponse;
import pe.dcs.app.features.smallgroup.response.SmallGroupPersonSearchResponse;
import pe.dcs.app.features.smallgroup.response.SmallGroupSearchRowResponse;
import pe.dcs.app.features.smallgroup.service.SmallGroupService;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

/**
 * Grupos pequeños / células: listado, detalle, búsqueda de persona
 * por DNI (líder o participante), crear/editar y gestión de
 * participantes.
 */
@RestController
@RequestMapping("/api/v1/small-group")
@RequiredArgsConstructor
public class SmallGroupController {

    private final SmallGroupService service;

    @PostMapping("/search")
    public ApiResponse<PageResponse<SmallGroupSearchRowResponse>> search(
            @RequestBody SmallGroupSearchRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Grupos pequeños obtenidos correctamente",
                service.search(request)
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<SmallGroupDetailResponse> getById(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Grupo pequeño obtenido correctamente",
                service.getById(id)
        );
    }

    @GetMapping("/find-by-dni")
    public ApiResponse<SmallGroupPersonSearchResponse> findPersonByDni(
            @RequestParam String dni
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Persona encontrada correctamente",
                service.findPersonByDni(dni)
        );
    }

    @PostMapping("/create")
    public ApiResponse<UUID> create(
            @RequestBody SmallGroupFormRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Grupo pequeño registrado correctamente",
                service.create(request)
        );
    }

    @PutMapping("/update/{id}")
    public ApiResponse<String> update(
            @PathVariable UUID id,
            @RequestBody SmallGroupFormRequest request
    ) {

        service.update(id, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Grupo pequeño actualizado correctamente",
                "OK"
        );
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<SmallGroupMemberResponse>> listMembers(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Participantes obtenidos correctamente",
                service.listMembers(id)
        );
    }

    @PostMapping("/{id}/members")
    public ApiResponse<String> addMember(
            @PathVariable UUID id,
            @RequestBody SmallGroupMemberFormRequest request
    ) {

        service.addMember(id, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Participante agregado correctamente",
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
                "Participante removido correctamente",
                "OK"
        );
    }
}
