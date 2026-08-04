package pe.dcs.app.features.event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.event.request.registration.EventPersonSearchRequest;
import pe.dcs.app.features.event.request.registration.EventRegistrationBulkRequest;
import pe.dcs.app.features.event.request.registration.EventRegistrationRequest;
import pe.dcs.app.features.event.request.registration.EventRegistrationSearchRequest;
import pe.dcs.app.features.event.response.registration.EventPersonSearchResponse;
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

    @PatchMapping("/pay/{id}")
    public ApiResponse<Void> markPaid(
            @PathVariable UUID id
    ) {

        service.markPaid(id);

        return new ApiResponse<>(
                200,
                "Inscripción marcada como pagada",
                null
        );
    }

    @PostMapping("/search-persons")
    public ApiResponse<PageResponse<EventPersonSearchResponse>> searchPersons(
            @RequestBody EventPersonSearchRequest request
    ) {

        return new ApiResponse<>(
                200,
                "Personas obtenidas",
                service.searchPersons(request)
        );
    }

    @PostMapping("/bulk-create")
    public ApiResponse<EventRegistrationBulkResponse> bulkCreate(
            @RequestBody EventRegistrationBulkRequest request
    ) {

        EventRegistrationBulkResponse result = service.bulkCreate(request);

        boolean hasFailures =
                result.getTotalFailed() != null && result.getTotalFailed() > 0;

        String message = hasFailures
                ? String.format(
                        "%d inscripción(es) registrada(s), %d no se pudieron registrar",
                        result.getTotalProcessed(),
                        result.getTotalFailed()
                )
                : "Inscripciones registradas correctamente";

        return new ApiResponse<>(
                200,
                message,
                result
        );
    }
}