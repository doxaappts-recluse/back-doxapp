package pe.dcs.app.features.marriage.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.marriage.request.MarriageFormRequest;
import pe.dcs.app.features.marriage.request.MarriageSearchRequest;
import pe.dcs.app.features.marriage.response.MarriageDetailResponse;
import pe.dcs.app.features.marriage.response.MarriageSearchRowResponse;
import pe.dcs.app.features.marriage.response.MarriageSpouseSearchResponse;
import pe.dcs.app.features.marriage.service.MarriageService;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/**
 * Matrimonios realizados en la iglesia: listado, detalle, búsqueda
 * de cónyuge por DNI, crear/editar.
 */
@RestController
@RequestMapping("/api/v1/marriage")
@RequiredArgsConstructor
public class MarriageController {

    private final MarriageService service;

    @PostMapping("/search")
    public ApiResponse<PageResponse<MarriageSearchRowResponse>> search(
            @RequestBody MarriageSearchRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Matrimonios obtenidos correctamente",
                service.search(request)
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<MarriageDetailResponse> getById(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Matrimonio obtenido correctamente",
                service.getById(id)
        );
    }

    @GetMapping("/find-by-dni")
    public ApiResponse<MarriageSpouseSearchResponse> findSpouseByDni(
            @RequestParam String dni
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Persona encontrada correctamente",
                service.findSpouseByDni(dni)
        );
    }

    @PostMapping("/create")
    public ApiResponse<String> create(
            @RequestBody MarriageFormRequest request
    ) {

        service.create(request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Matrimonio registrado correctamente",
                "OK"
        );
    }

    @PutMapping("/update/{id}")
    public ApiResponse<String> update(
            @PathVariable UUID id,
            @RequestBody MarriageFormRequest request
    ) {

        service.update(id, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Matrimonio actualizado correctamente",
                "OK"
        );
    }

}
