package pe.dcs.app.features.hr.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.util.List;

@Getter
@Setter
public class LeaveRequestSearchRequest {

    private LeaveRequestFilterRequest filters;

    private PaginationRequest pagination;

    private List<SortRequest> sorts;
}
