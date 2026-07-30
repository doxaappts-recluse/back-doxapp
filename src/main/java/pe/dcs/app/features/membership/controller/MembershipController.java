package pe.dcs.app.features.membership.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.membership.request.MembershipFormRequest;
import pe.dcs.app.features.membership.request.MembershipHistoryRequest;
import pe.dcs.app.features.membership.request.MembershipSearchRequest;
import pe.dcs.app.features.membership.response.MembershipContextResponse;
import pe.dcs.app.features.membership.response.MembershipDetailResponse;
import pe.dcs.app.features.membership.response.MembershipSearchRowResponse;
import pe.dcs.app.features.membership.service.MembershipService;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/**
 * Membresía de personas: listado con su membresía vigente,
 * ver/crear/editar membresía de una persona puntual e
 * historial de membresías de esa persona.
 */
@RestController
@RequestMapping("/api/v1/membership-user")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipService service;

    @PostMapping("/search")
    public ApiResponse<PageResponse<MembershipSearchRowResponse>> search(
            @RequestBody MembershipSearchRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Membresías obtenidas correctamente",
                service.search(request)
        );
    }

    @GetMapping("/current/{userId}")
    public ApiResponse<MembershipContextResponse> getCurrent(
            @PathVariable UUID userId
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Membresía vigente obtenida correctamente",
                service.getCurrent(userId)
        );
    }

    @PostMapping("/create/{userId}")
    public ApiResponse<String> create(
            @PathVariable UUID userId,
            @RequestBody MembershipFormRequest request
    ) {

        service.create(userId, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Membresía creada correctamente",
                "OK"
        );
    }

    @PutMapping("/update/{userId}/{membershipId}")
    public ApiResponse<String> update(
            @PathVariable UUID userId,
            @PathVariable UUID membershipId,
            @RequestBody MembershipFormRequest request
    ) {

        service.update(userId, membershipId, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Membresía actualizada correctamente",
                "OK"
        );
    }

    @PostMapping("/history/{userId}")
    public ApiResponse<PageResponse<MembershipDetailResponse>> history(
            @PathVariable UUID userId,
            @RequestBody MembershipHistoryRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Historial de membresías obtenido correctamente",
                service.history(userId, request)
        );
    }

}
