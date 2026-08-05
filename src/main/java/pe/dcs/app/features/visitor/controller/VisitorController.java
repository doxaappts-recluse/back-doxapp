package pe.dcs.app.features.visitor.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.visitor.request.VisitorConvertToMemberRequest;
import pe.dcs.app.features.visitor.request.VisitorFormRequest;
import pe.dcs.app.features.visitor.request.VisitorSearchRequest;
import pe.dcs.app.features.visitor.response.VisitorDetailResponse;
import pe.dcs.app.features.visitor.response.VisitorSearchRowResponse;
import pe.dcs.app.features.visitor.service.VisitorService;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/**
 * Visitantes: listado, detalle/crear/editar por persona, conversión
 * a miembro. Mismo patrón que Bautizo/Matrimonio (1:1 sobre una
 * Person que ya existe, creada vía el módulo Usuarios).
 */
@RestController
@RequestMapping("/api/v1/visitor")
@RequiredArgsConstructor
public class VisitorController {

    private final VisitorService service;

    @PostMapping("/search")
    public ApiResponse<PageResponse<VisitorSearchRowResponse>> search(
            @RequestBody VisitorSearchRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.visitantesObtenidosCorrectamente",
                service.search(request)
        );
    }

    @GetMapping("/{personId}")
    public ApiResponse<VisitorDetailResponse> getByPersonId(
            @PathVariable UUID personId
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.visitanteObtenidoCorrectamente",
                service.getByPersonId(personId)
        );
    }

    @PostMapping("/{personId}")
    public ApiResponse<String> create(
            @PathVariable UUID personId,
            @Valid @RequestBody VisitorFormRequest request
    ) {

        service.create(personId, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.visitanteRegistradoCorrectamente",
                "OK"
        );
    }

    @PutMapping("/{personId}")
    public ApiResponse<String> update(
            @PathVariable UUID personId,
            @Valid @RequestBody VisitorFormRequest request
    ) {

        service.update(personId, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.visitanteActualizadoCorrectamente",
                "OK"
        );
    }

    @PostMapping("/{personId}/convert-to-member")
    public ApiResponse<String> convertToMember(
            @PathVariable UUID personId,
            @Valid @RequestBody VisitorConvertToMemberRequest request
    ) {

        service.convertToMember(personId, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.visitanteConvertidoMiembroCorrectamente",
                "OK"
        );
    }
}
