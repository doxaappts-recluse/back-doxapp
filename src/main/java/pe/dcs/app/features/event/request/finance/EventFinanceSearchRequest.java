package pe.dcs.app.features.event.request.finance;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.util.List;

@Getter
@Setter
public class EventFinanceSearchRequest {

    private EventFinanceFilter filters;

    private PaginationRequest pagination;

    private List<SortRequest> sorts;
}