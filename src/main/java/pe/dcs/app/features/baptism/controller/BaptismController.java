package pe.dcs.app.features.baptism.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.baptism.request.BaptismFormRequest;
import pe.dcs.app.features.baptism.request.BaptismSearchRequest;
import pe.dcs.app.features.baptism.response.BaptismContextResponse;
import pe.dcs.app.features.baptism.response.BaptismSearchRowResponse;
import pe.dcs.app.features.baptism.service.BaptismService;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/**
 * Bautizo de personas: listado con su registro de bautizo,
 * ver/crear/editar el bautizo de una persona puntual.
 */
@RestController
@RequestMapping("/api/v1/baptism")
@RequiredArgsConstructor
public class BaptismController {

    private final BaptismService service;

    @PostMapping("/search")
    public ApiResponse<PageResponse<BaptismSearchRowResponse>> search(
            @RequestBody BaptismSearchRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.bautizosObtenidosCorrectamente",
                service.search(request)
        );
    }

    @GetMapping("/current/{userId}")
    public ApiResponse<BaptismContextResponse> getCurrent(
            @PathVariable UUID userId
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.bautizoObtenidoCorrectamente",
                service.getCurrent(userId)
        );
    }

    @PostMapping("/create/{userId}")
    public ApiResponse<String> create(
            @PathVariable UUID userId,
            @Valid @RequestBody BaptismFormRequest request
    ) {

        service.create(userId, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.bautizoRegistradoCorrectamente",
                "OK"
        );
    }

    @PutMapping("/update/{userId}/{baptismId}")
    public ApiResponse<String> update(
            @PathVariable UUID userId,
            @PathVariable UUID baptismId,
            @Valid @RequestBody BaptismFormRequest request
    ) {

        service.update(userId, baptismId, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.bautizoActualizadoCorrectamente",
                "OK"
        );
    }

}
