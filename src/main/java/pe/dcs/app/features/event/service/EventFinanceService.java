package pe.dcs.app.features.event.service;

import pe.dcs.app.features.event.request.finance.EventFinanceApproveRequest;
import pe.dcs.app.features.event.request.finance.EventFinanceRejectRequest;
import pe.dcs.app.features.event.request.finance.EventFinanceRequest;
import pe.dcs.app.features.event.request.finance.EventFinanceSearchRequest;
import pe.dcs.app.features.event.response.finance.EventFinanceResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

public interface EventFinanceService {

    EventFinanceResponse create(
            EventFinanceRequest request
    );

    EventFinanceResponse update(
            UUID id,
            EventFinanceRequest request
    );

    EventFinanceResponse approve(
            UUID id,
            EventFinanceApproveRequest request
    );

    EventFinanceResponse reject(
            UUID id,
            EventFinanceRejectRequest request
    );

    EventFinanceResponse getById(
            UUID id
    );

    PageResponse<EventFinanceResponse> search(
            EventFinanceSearchRequest request
    );
}