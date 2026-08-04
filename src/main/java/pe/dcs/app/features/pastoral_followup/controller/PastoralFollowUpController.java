package pe.dcs.app.features.pastoral_followup.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.pastoral_followup.request.AssignLeaderRequest;
import pe.dcs.app.features.pastoral_followup.request.FollowUpContactFormRequest;
import pe.dcs.app.features.pastoral_followup.request.InactiveMemberSearchRequest;
import pe.dcs.app.features.pastoral_followup.request.PastoralFollowUpHistoryRequest;
import pe.dcs.app.features.pastoral_followup.request.PrayerRequestFormRequest;
import pe.dcs.app.features.pastoral_followup.response.FollowUpContactResponse;
import pe.dcs.app.features.pastoral_followup.response.InactiveMemberResponse;
import pe.dcs.app.features.pastoral_followup.response.PastoralFollowUpSummaryResponse;
import pe.dcs.app.features.pastoral_followup.response.PrayerRequestResponse;
import pe.dcs.app.features.pastoral_followup.service.PastoralFollowUpService;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/**
 * Seguimiento pastoral de una Person cualquiera: líder asignado,
 * historial de contactos y peticiones de oración. Se consume desde
 * el detalle de la persona (ver tarea frontend #384) — no tiene
 * listado propio a nivel de módulo, siempre está anclado a un
 * personId puntual.
 */
@RestController
@RequestMapping("/api/v1/pastoral-followup")
@RequiredArgsConstructor
public class PastoralFollowUpController {

    private final PastoralFollowUpService service;

    @GetMapping("/{personId}/summary")
    public ApiResponse<PastoralFollowUpSummaryResponse> getSummary(
            @PathVariable UUID personId
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Resumen de seguimiento pastoral obtenido correctamente",
                service.getSummary(personId)
        );
    }

    @PostMapping("/{personId}/assign-leader")
    public ApiResponse<String> assignLeader(
            @PathVariable UUID personId,
            @RequestBody AssignLeaderRequest request
    ) {

        service.assignLeader(personId, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Líder de seguimiento asignado correctamente",
                "OK"
        );
    }

    @PostMapping("/{personId}/contacts/search")
    public ApiResponse<PageResponse<FollowUpContactResponse>> listContacts(
            @PathVariable UUID personId,
            @RequestBody PastoralFollowUpHistoryRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Historial de contactos obtenido correctamente",
                service.listContacts(personId, request)
        );
    }

    @PostMapping("/{personId}/contacts")
    public ApiResponse<String> createContact(
            @PathVariable UUID personId,
            @RequestBody FollowUpContactFormRequest request
    ) {

        service.createContact(personId, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Contacto registrado correctamente",
                "OK"
        );
    }

    @PutMapping("/{personId}/contacts/{contactId}")
    public ApiResponse<String> updateContact(
            @PathVariable UUID personId,
            @PathVariable UUID contactId,
            @RequestBody FollowUpContactFormRequest request
    ) {

        service.updateContact(personId, contactId, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Contacto actualizado correctamente",
                "OK"
        );
    }

    @PostMapping("/{personId}/prayer-requests/search")
    public ApiResponse<PageResponse<PrayerRequestResponse>> listPrayerRequests(
            @PathVariable UUID personId,
            @RequestBody PastoralFollowUpHistoryRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Peticiones de oración obtenidas correctamente",
                service.listPrayerRequests(personId, request)
        );
    }

    @PostMapping("/{personId}/prayer-requests")
    public ApiResponse<String> createPrayerRequest(
            @PathVariable UUID personId,
            @RequestBody PrayerRequestFormRequest request
    ) {

        service.createPrayerRequest(personId, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Petición de oración registrada correctamente",
                "OK"
        );
    }

    @PutMapping("/{personId}/prayer-requests/{prayerRequestId}")
    public ApiResponse<String> updatePrayerRequest(
            @PathVariable UUID personId,
            @PathVariable UUID prayerRequestId,
            @RequestBody PrayerRequestFormRequest request
    ) {

        service.updatePrayerRequest(personId, prayerRequestId, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Petición de oración actualizada correctamente",
                "OK"
        );
    }

    /**
     * Miembros inactivos (CRM Pastoral): cruza Membership con lo que
     * ya existe en Seguimiento Pastoral. No va anidado bajo un
     * personId — es un listado propio, mismo nivel que /search en
     * otros módulos.
     */
    @PostMapping("/inactive-members/search")
    public ApiResponse<PageResponse<InactiveMemberResponse>> searchInactiveMembers(
            @RequestBody InactiveMemberSearchRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Miembros inactivos obtenidos correctamente",
                service.searchInactiveMembers(request)
        );
    }
}
