package pe.dcs.app.features.baptism.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.util.List;

@Getter
@Setter
public class BaptismSearchRequest {

    private BaptismFilterRequest filters;

    private PaginationRequest pagination;

    private List<SortRequest> sorts;
}
