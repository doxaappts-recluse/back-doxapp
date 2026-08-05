package pe.dcs.app.features.finance;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.FinancialFund;
import pe.dcs.app.features.finance.response.FinancialFundResponse;
import pe.dcs.app.util.auditable.BaseMapper;

@Component
public class FinancialFundMapper {

    public FinancialFundResponse simple(FinancialFund fund, boolean showAudit) {

        FinancialFundResponse response = new FinancialFundResponse();

        BaseMapper.mapAudit(fund, response, showAudit);

        response.setId(fund.getId());
        response.setOrganizationId(fund.getOrganization().getId());
        response.setCode(fund.getCode());
        response.setNameEs(fund.getNameEs());
        response.setNameEn(fund.getNameEn());
        response.setName(fund.getLocalizedName());
        response.setDescription(fund.getDescription());
        response.setStatus(fund.getStatus().name());

        return response;
    }
}
