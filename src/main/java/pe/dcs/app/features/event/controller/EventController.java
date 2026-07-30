package pe.dcs.app.features.event.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.dcs.app.entity.Event;
import pe.dcs.app.features.event.request.event.EventRequest;
import pe.dcs.app.features.event.request.event.EventSearchRequest;
import pe.dcs.app.features.event.response.event.EventDetailResponse;
import pe.dcs.app.features.event.response.event.EventResponse;
import pe.dcs.app.features.event.service.EventService;
import pe.dcs.app.service.supabase.SupabaseStorageService;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService eventService;
    private final SupabaseStorageService storageService;

    @PostMapping("/create")
    public ApiResponse<EventDetailResponse> create(
            @Valid @RequestPart("event") EventRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        return new ApiResponse<>(
                200,
                "Event created",
                eventService.create(request, file)
        );
    }

    @PutMapping("/update/{id}")
    public ApiResponse<EventDetailResponse> update(
            @PathVariable UUID id,
            @Valid @RequestPart("event") EventRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        return new ApiResponse<>(
                200,
                "Event updated",
                eventService.update(id, request, file)
        );
    }

    @GetMapping("/getBy/{id}")
    public ApiResponse<EventDetailResponse> getById(
            @PathVariable UUID id
    ) {
        return new ApiResponse<>(
                200,
                "Evento obtenido correctamente",
                eventService.getById(id)
        );
    }

    @PostMapping("/search")
    public ApiResponse<PageResponse<EventResponse>> search(
            @RequestBody
                    EventSearchRequest request
    ) {
        return new ApiResponse<>(200, "Eventos fetched", eventService.search(request));
    }

    @PatchMapping("/publish/{id}")
    public ApiResponse<EventDetailResponse> publish(
            @PathVariable UUID id
    ) {
        return new ApiResponse<>(200, "Events published", eventService.publish(id));

    }

    @PatchMapping("/cancel/{id}")
    public ApiResponse<EventDetailResponse> cancel(
            @PathVariable UUID id
    ) {
        return new ApiResponse<>(200, "Events cancelled", eventService.cancel(id));
    }

    @GetMapping("/events/{id}/image")
    public String getEventImage(@PathVariable UUID id) {

        Event event = eventService.findById(id);

        return storageService.createSignedUrl(
                "events",
                event.getTemplatePath(),
                3600
        );

    }
}