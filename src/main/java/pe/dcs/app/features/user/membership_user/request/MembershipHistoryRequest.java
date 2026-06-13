package pe.dcs.app.features.user.membership_user.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.util.List;

@Getter
@Setter
public class MembershipHistoryRequest {

    private PaginationRequest pagination;

    private List<SortRequest> sorts;
}