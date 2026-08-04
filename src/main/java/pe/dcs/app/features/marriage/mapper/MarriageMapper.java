package pe.dcs.app.features.marriage.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Marriage;
import pe.dcs.app.features.marriage.response.MarriageDetailResponse;
import pe.dcs.app.features.marriage.response.MarriageSearchRowResponse;
import pe.dcs.app.util.auditable.BaseMapper;

@Component
public class MarriageMapper {

    public MarriageSearchRowResponse toSearchRow(
            Marriage marriage,
            boolean showAudit
    ) {

        MarriageSearchRowResponse row = new MarriageSearchRowResponse();

        BaseMapper.mapAudit(marriage, row, showAudit);

        row.setId(marriage.getId());
        row.setSpouse1Name(marriage.getSpouse1Name());
        row.setSpouse2Name(marriage.getSpouse2Name());
        row.setMarriageDate(marriage.getMarriageDate());
        row.setChurchName(marriage.getChurchName());
        row.setVerified(marriage.isVerified());
        row.setFeeAmount(marriage.getFeeAmount());

        if (marriage.getBranch() != null) {
            row.setBranchId(marriage.getBranch().getId());
            row.setBranchName(marriage.getBranch().getName());
        }

        return row;
    }

    /**
     * spouse1Member/spouse2Member se calculan en el service (con
     * MembershipRepository) y se pasan acá ya resueltos — el mapper
     * se mantiene sin dependencias, mismo criterio que el resto de
     * los mappers del proyecto.
     */
    public MarriageDetailResponse toDetailResponse(
            Marriage marriage,
            boolean spouse1Member,
            boolean spouse2Member
    ) {

        MarriageDetailResponse response = new MarriageDetailResponse();

        response.setId(marriage.getId());

        response.setSpouse1Name(marriage.getSpouse1Name());
        response.setSpouse1Dni(marriage.getSpouse1Dni());
        response.setSpouse1Member(spouse1Member);

        response.setSpouse2Name(marriage.getSpouse2Name());
        response.setSpouse2Dni(marriage.getSpouse2Dni());
        response.setSpouse2Member(spouse2Member);

        if (marriage.getSpouse1Person() != null) {
            response.setSpouse1PersonId(marriage.getSpouse1Person().getId());
        }

        if (marriage.getSpouse2Person() != null) {
            response.setSpouse2PersonId(marriage.getSpouse2Person().getId());
        }

        response.setMarriageDate(marriage.getMarriageDate());
        response.setChurchName(marriage.getChurchName());
        response.setPastorName(marriage.getPastorName());
        response.setCity(marriage.getCity());
        response.setVerified(marriage.isVerified());
        response.setObservations(marriage.getObservations());
        response.setFeeAmount(marriage.getFeeAmount());

        if (marriage.getFinancialMovement() != null) {
            response.setFinancialMovementId(marriage.getFinancialMovement().getId());
        }

        if (marriage.getBranch() != null) {
            response.setBranchId(marriage.getBranch().getId());
            response.setBranchName(marriage.getBranch().getName());
        }

        return response;
    }
}
