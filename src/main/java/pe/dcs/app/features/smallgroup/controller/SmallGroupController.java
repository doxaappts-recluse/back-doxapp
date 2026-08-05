package pe.dcs.app.features.smallgroup.controller;

import jakarta.validation.Valid;
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
                "success.gruposPequenosObtenidosCorrectamente",
                service.search(request)
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<SmallGroupDetailResponse> getById(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.grupoPequenoObtenidoCorrectamente",
                service.getById(id)
        );
    }

    @GetMapping("/find-by-dni")
    public ApiResponse<SmallGroupPersonSearchResponse> findPersonByDni(
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
            @Valid @RequestBody SmallGroupFormRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.grupoPequenoRegistradoCorrectamente",
                service.create(request)
        );
    }

    @PutMapping("/update/{id}")
    public ApiResponse<String> update(
            @PathVariable UUID id,
            @Valid @RequestBody SmallGroupFormRequest request
    ) {

        service.update(id, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.grupoPequenoActualizadoCorrectamente",
                "OK"
        );
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<SmallGroupMemberResponse>> listMembers(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.participantesObtenidosCorrectamente",
                service.listMembers(id)
        );
    }

    @PostMapping("/{id}/members")
    public ApiResponse<String> addMember(
            @PathVariable UUID id,
            @Valid @RequestBody SmallGroupMemberFormRequest request
    ) {

        service.addMember(id, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.participanteAgregadoCorrectamente",
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
                "success.participanteRemovidoCorrectamente",
                "OK"
        );
    }
}
