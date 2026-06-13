package pe.dcs.app.features.ministry_role.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.util.List;

@Getter
@Setter
public class MinistryRoleSearchRequest {

    private MinistryRoleFilter filters;
    private PaginationRequest pagination;
    private List<SortRequest> sorts;
}