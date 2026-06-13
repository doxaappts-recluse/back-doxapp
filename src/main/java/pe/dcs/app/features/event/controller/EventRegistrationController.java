package pe.dcs.app.features.event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.event.request.registration.EventRegistrationBulkRequest;
import pe.dcs.app.features.event.request.registration.EventRegistrationRequest;
import pe.dcs.app.features.event.request.registration.EventRegistrationSearchRequest;
import pe.dcs.app.features.event.response.registration.EventRegistrationBulkResponse;
import pe.dcs.app.features.event.response.registration.EventRegistrationDetailResponse;
import pe.dcs.app.features.event.response.registration.EventRegistrationResponse;
import pe.dcs.app.features.event.service.EventRegistrationService;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/event-registrations")
@RequiredArgsConstructor
public class EventRegistrationController {

    private final EventRegistrationService service;

    @PostMapping("/create")
    public ApiResponse<EventRegistrationResponse> create(
            @RequestBody EventRegistrationRequest request
    ) {
        service.create(request);
        return new ApiResponse<>(
                200,
                "Inscripción creada",
                null
        );
    }

    @PutMapping("/update/{id}")
    public ApiResponse<EventRegistrationResponse> update(
            @PathVariable UUID id,
            @RequestBody EventRegistrationRequest request
    ) {
        service.update(id, request);
        return new ApiResponse<>(
                200,
                "Inscripción actualizada",
                null
        );
    }

    @GetMapping("/find/{id}")
    public ApiResponse<EventRegistrationDetailResponse> find(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                200,
                "Inscripción encontrada",
                service.getById(id)
        );
    }

    @PostMapping("/search")
    public ApiResponse<PageResponse<EventRegistrationResponse>> search(
            @RequestBody EventRegistrationSearchRequest request
    ) {

        return new ApiResponse<>(
                200,
                "Inscripciones obtenidas",
                service.search(request)
        );
    }

    @PatchMapping("/cancel/{id}")
    public ApiResponse<Void> cancel(
            @PathVariable UUID id
    ) {

        service.cancel(id);

        return new ApiResponse<>(
                200,
                "Inscripción cancelada",
                null
        );
    }

    @PostMapping("/bulk-create")
    public ApiResponse<EventRegistrationBulkResponse> bulkCreate(
            @RequestBody EventRegistrationBulkRequest request
    ) {
        service.bulkCreate(request);
        return new ApiResponse<>(
                200,
                "Inscripciones registradas correctamente",
                null
        );
    }
}