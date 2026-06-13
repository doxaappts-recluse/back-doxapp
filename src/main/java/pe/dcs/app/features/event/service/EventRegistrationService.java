package pe.dcs.app.features.event.service;

import pe.dcs.app.features.event.request.registration.EventRegistrationBulkRequest;
import pe.dcs.app.features.event.request.registration.EventRegistrationRequest;
import pe.dcs.app.features.event.request.registration.EventRegistrationSearchRequest;
import pe.dcs.app.features.event.response.registration.EventRegistrationBulkResponse;
import pe.dcs.app.features.event.response.registration.EventRegistrationDetailResponse;
import pe.dcs.app.features.event.response.registration.EventRegistrationResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

public interface EventRegistrationService {

    EventRegistrationResponse create(
            EventRegistrationRequest request
    );

    EventRegistrationResponse update(
            UUID id,
            EventRegistrationRequest request
    );

    EventRegistrationDetailResponse getById(
            UUID id
    );

    PageResponse<EventRegistrationResponse> search(
            EventRegistrationSearchRequest request
    );

    void cancel(
            UUID id
    );

    EventRegistrationBulkResponse bulkCreate(
            EventRegistrationBulkRequest request
    );
}