package pe.dcs.app.features.finance;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.FinancialMovement;
import pe.dcs.app.entity.Person;
import pe.dcs.app.features.finance.response.FinancialMovementResponse;
import pe.dcs.app.util.auditable.BaseMapper;

@Component
public class FinancialMovementMapper {

    public FinancialMovementResponse simple(
            FinancialMovement movement,
            boolean showAudit
    ) {

        FinancialMovementResponse response =
                new FinancialMovementResponse();

        BaseMapper.mapAudit(movement, response, showAudit);

        response.setId(movement.getId());

        response.setBranchId(movement.getBranch().getId());
        response.setBranchName(movement.getBranch().getName());

        response.setType(movement.getType());
        response.setCategory(movement.getCategory());
        response.setStatus(movement.getStatus());

        response.setPaymentMethod(movement.getPaymentMethod());

        response.setConcept(movement.getConcept());
        response.setAmount(movement.getAmount());
        response.setMovementDate(movement.getMovementDate());
        response.setObservations(movement.getObservations());

        response.setApprovedAt(movement.getApprovedAt());

        if (movement.getPerson() != null) {

            response.setPersonId(movement.getPerson().getId());
            response.setPersonName(movement.getPerson().getName());
            response.setPersonLastname(movement.getPerson().getLastname());
        }

        if (movement.getFund() != null) {

            response.setFundId(movement.getFund().getId());
            response.setFundName(movement.getFund().getLocalizedName());
        }

        if (movement.getCreatedByUser() != null) {

            response.setCreatedByUserId(
                    movement.getCreatedByUser().getId()
            );

            response.setCreatedByUserName(
                    buildFullName(movement.getCreatedByUser())
            );
        }

        if (movement.getApprovedByUser() != null) {

            response.setApprovedByUserId(
                    movement.getApprovedByUser().getId()
            );

            response.setApprovedByUserName(
                    buildFullName(movement.getApprovedByUser())
            );
        }

        return response;
    }

    private String buildFullName(Person user) {
        return String.format(
                "%s %s",
                user.getName(),
                user.getLastname()
        ).trim();
    }
}
