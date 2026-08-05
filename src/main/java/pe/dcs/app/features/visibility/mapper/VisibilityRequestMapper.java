package pe.dcs.app.features.visibility.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.VisibilityRequest;
import pe.dcs.app.features.visibility.response.VisibilityRequestRowResponse;
import pe.dcs.app.util.auditable.BaseMapper;

@Component
public class VisibilityRequestMapper {

    public VisibilityRequestRowResponse toRow(
            VisibilityRequest request,
            boolean showAudit,
            boolean canDecide
    ) {

        VisibilityRequestRowResponse row = new VisibilityRequestRowResponse();

        BaseMapper.mapAudit(request, row, showAudit);

        row.setId(request.getId());

        row.setPersonId(request.getPerson().getId());
        row.setPersonName(request.getPerson().getName());
        row.setPersonLastname(request.getPerson().getLastname());

        row.setModuleCode(request.getModule().getCode());
        row.setModuleName(request.getModule().getLocalizedName());

        row.setSourceBranchId(request.getSourceBranch().getId());
        row.setSourceBranchName(request.getSourceBranch().getName());

        row.setRequestBranchId(request.getRequestBranch().getId());
        row.setRequestBranchName(request.getRequestBranch().getName());

        row.setRequestedByName(
                request.getRequestedBy() != null
                        ? request.getRequestedBy().getName()
                                + " "
                                + request.getRequestedBy().getLastname()
                        : null
        );

        row.setReason(request.getReason());
        row.setRequestedFrom(request.getRequestedFrom());
        row.setRequestedUntil(request.getRequestedUntil());
        row.setApprovedUntil(request.getApprovedUntil());
        row.setStatus(request.getStatus());
        row.setCanDecide(canDecide);

        return row;
    }
}
