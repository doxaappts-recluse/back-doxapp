package pe.dcs.app.features.contract.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.util.List;

@Getter
@Setter
public class ContractListRequest {

    private ContractFilterRequest filters;

    private PaginationRequest pagination;

    private List<SortRequest> sorts;
}