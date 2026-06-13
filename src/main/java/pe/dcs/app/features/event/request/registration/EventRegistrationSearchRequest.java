package pe.dcs.app.features.event.request.registration;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.util.List;

@Getter
@Setter
public class EventRegistrationSearchRequest {
    private EventRegistrationFilter filters;
    private PaginationRequest pagination;
    private List<SortRequest> sorts;
}