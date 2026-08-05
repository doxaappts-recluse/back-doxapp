package pe.dcs.app.features.finance;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.FinancialBudget;
import pe.dcs.app.features.finance.response.FinancialBudgetResponse;
import pe.dcs.app.util.auditable.BaseMapper;

@Component
public class FinancialBudgetMapper {

    public FinancialBudgetResponse simple(FinancialBudget budget, boolean showAudit) {

        FinancialBudgetResponse response = new FinancialBudgetResponse();

        BaseMapper.mapAudit(budget, response, showAudit);

        response.setId(budget.getId());
        response.setOrganizationId(budget.getOrganization().getId());

        if (budget.getBranch() != null) {
            response.setBranchId(budget.getBranch().getId());
            response.setBranchName(budget.getBranch().getName());
        }

        if (budget.getFund() != null) {
            response.setFundId(budget.getFund().getId());
            response.setFundName(budget.getFund().getLocalizedName());
        }

        if (budget.getCategory() != null) {
            response.setCategory(budget.getCategory().name());
        }

        response.setPeriodYear(budget.getPeriodYear());
        response.setPeriodMonth(budget.getPeriodMonth());
        response.setName(budget.getName());
        response.setDescription(budget.getDescription());
        response.setAmount(budget.getAmount());
        response.setStatus(budget.getStatus().name());

        return response;
    }
}
