package pe.dcs.app.features.space_reservation.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.space_reservation.request.ReservableSpaceFormRequest;
import pe.dcs.app.features.space_reservation.request.ReservableSpaceSearchRequest;
import pe.dcs.app.features.space_reservation.request.SpaceReservationFormRequest;
import pe.dcs.app.features.space_reservation.request.SpaceReservationSearchRequest;
import pe.dcs.app.features.space_reservation.response.ReservableSpaceResponse;
import pe.dcs.app.features.space_reservation.response.SpaceReservationPersonSearchResponse;
import pe.dcs.app.features.space_reservation.response.SpaceReservationResponse;
import pe.dcs.app.features.space_reservation.service.SpaceReservationService;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/**
 * Reservas de Espacios: catálogo de espacios por sede (no delegable)
 * y reservas (delegables, confirmación inmediata, con solapamiento
 * bloqueado por espacio). SYSTEM no tiene acceso — ver
 * SpaceReservationAccessGuard.
 */
@RestController
@RequestMapping("/api/v1/space-reservation")
@RequiredArgsConstructor
public class SpaceReservationController {

    private final SpaceReservationService service;

    @GetMapping("/find-by-dni")
    public ApiResponse<SpaceReservationPersonSearchResponse> findPersonByDni(@RequestParam String dni) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Persona encontrada correctamente",
                service.findPersonByDni(dni)
        );
    }

    // =====================================================
    // ESPACIOS
    // =====================================================

    @PostMapping("/spaces/search")
    public ApiResponse<PageResponse<ReservableSpaceResponse>> searchSpaces(@RequestBody ReservableSpaceSearchRequest request) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Espacios obtenidos correctamente",
                service.searchSpaces(request)
        );
    }

    @GetMapping("/spaces/{id}")
    public ApiResponse<ReservableSpaceResponse> getSpaceById(@PathVariable UUID id) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Espacio obtenido correctamente",
                service.getSpaceById(id)
        );
    }

    @PostMapping("/spaces/create")
    public ApiResponse<UUID> createSpace(@RequestBody ReservableSpaceFormRequest request) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Espacio registrado correctamente",
                service.createSpace(request)
        );
    }

    @PutMapping("/spaces/update/{id}")
    public ApiResponse<String> updateSpace(@PathVariable UUID id, @RequestBody ReservableSpaceFormRequest request) {
        service.updateSpace(id, request);
        return new ApiResponse<>(HttpStatus.OK.value(), "Espacio actualizado correctamente", "OK");
    }

    // =====================================================
    // RESERVAS
    // =====================================================

    @PostMapping("/reservations/search")
    public ApiResponse<PageResponse<SpaceReservationResponse>> searchReservations(@RequestBody SpaceReservationSearchRequest request) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Reservas obtenidas correctamente",
                service.searchReservations(request)
        );
    }

    @GetMapping("/reservations/{id}")
    public ApiResponse<SpaceReservationResponse> getReservationById(@PathVariable UUID id) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Reserva obtenida correctamente",
                service.getReservationById(id)
        );
    }

    @PostMapping("/reservations/create")
    public ApiResponse<UUID> createReservation(@RequestBody SpaceReservationFormRequest request) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Reserva registrada correctamente",
                service.createReservation(request)
        );
    }

    @PutMapping("/reservations/update/{id}")
    public ApiResponse<String> updateReservation(@PathVariable UUID id, @RequestBody SpaceReservationFormRequest request) {
        service.updateReservation(id, request);
        return new ApiResponse<>(HttpStatus.OK.value(), "Reserva actualizada correctamente", "OK");
    }

    @PostMapping("/reservations/{id}/cancel")
    public ApiResponse<String> cancelReservation(@PathVariable UUID id) {
        service.cancelReservation(id);
        return new ApiResponse<>(HttpStatus.OK.value(), "Reserva cancelada correctamente", "OK");
    }
}
