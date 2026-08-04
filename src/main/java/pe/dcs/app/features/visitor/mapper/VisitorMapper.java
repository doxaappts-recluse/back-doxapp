package pe.dcs.app.features.visitor.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Visitor;
import pe.dcs.app.features.visitor.response.VisitorDetailResponse;
import pe.dcs.app.features.visitor.response.VisitorSearchRowResponse;
import pe.dcs.app.util.auditable.BaseMapper;

@Component
public class VisitorMapper {

    public VisitorSearchRowResponse toSearchRow(
            Visitor visitor,
            boolean showAudit
    ) {

        VisitorSearchRowResponse row = new VisitorSearchRowResponse();

        BaseMapper.mapAudit(visitor, row, showAudit);

        row.setId(visitor.getId());

        row.setPersonId(visitor.getPerson().getId());
        row.setPersonName(visitor.getPerson().getName());
        row.setPersonLastname(visitor.getPerson().getLastname());
        row.setPersonDni(visitor.getPerson().getDni());

        row.setFirstVisitDate(visitor.getFirstVisitDate());
        row.setHowArrived(visitor.getHowArrived());
        row.setConsolidationStage(visitor.getConsolidationStage());
        row.setConvertedAt(visitor.getConvertedAt());

        if (visitor.getBranch() != null) {
            row.setBranchId(visitor.getBranch().getId());
            row.setBranchName(visitor.getBranch().getName());
        }

        return row;
    }

    /**
     * alreadyMember se calcula en el service (con
     * MembershipRepository) y se pasa acá ya resuelto — mismo
     * criterio que MarriageMapper.toDetailResponse(spouse1Member,...).
     */
    public VisitorDetailResponse toDetailResponse(
            Visitor visitor,
            boolean alreadyMember
    ) {

        VisitorDetailResponse response = new VisitorDetailResponse();

        response.setId(visitor.getId());

        response.setPersonId(visitor.getPerson().getId());
        response.setPersonName(visitor.getPerson().getName());
        response.setPersonLastname(visitor.getPerson().getLastname());
        response.setPersonDni(visitor.getPerson().getDni());

        response.setFirstVisitDate(visitor.getFirstVisitDate());
        response.setHowArrived(visitor.getHowArrived());

        if (visitor.getInvitedBy() != null) {
            response.setInvitedByPersonId(visitor.getInvitedBy().getId());
            response.setInvitedByName(
                    visitor.getInvitedBy().getName() + " " + visitor.getInvitedBy().getLastname()
            );
        }

        response.setConsolidationStage(visitor.getConsolidationStage());
        response.setConvertedAt(visitor.getConvertedAt());
        response.setNotes(visitor.getNotes());

        if (visitor.getBranch() != null) {
            response.setBranchId(visitor.getBranch().getId());
            response.setBranchName(visitor.getBranch().getName());
        }

        response.setAlreadyMember(alreadyMember);

        return response;
    }
}
