package pe.dcs.app.features.event.service;

import org.springframework.web.multipart.MultipartFile;
import pe.dcs.app.entity.Event;
import pe.dcs.app.features.event.request.event.EventRequest;
import pe.dcs.app.features.event.request.event.EventSearchRequest;
import pe.dcs.app.features.event.response.event.EventDetailResponse;
import pe.dcs.app.features.event.response.event.EventResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

public interface EventService {

    EventDetailResponse create(
            EventRequest request,
            MultipartFile file
    );

    EventDetailResponse update(
            UUID id,
            EventRequest request,
            MultipartFile file
    );

    EventDetailResponse getById(
            UUID id
    );

    PageResponse<EventResponse> search(
            EventSearchRequest request
    );

    EventDetailResponse publish(
            UUID id
    );

    EventDetailResponse cancel(
            UUID id
    );

    Event findById(UUID id);
}