package pe.dcs.app.features.branch_transfer.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.util.List;

@Getter
@Setter
public class BranchTransferHistoryRequest {

    private PaginationRequest pagination;

    private List<SortRequest> sorts;
}
