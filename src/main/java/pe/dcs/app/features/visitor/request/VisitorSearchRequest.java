package pe.dcs.app.features.visitor.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.features.visitor.VisitorFilterRequest;
import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.util.List;

@Getter
@Setter
public class VisitorSearchRequest {

    private VisitorFilterRequest filters;

    private PaginationRequest pagination;

    private List<SortRequest> sorts;
}
