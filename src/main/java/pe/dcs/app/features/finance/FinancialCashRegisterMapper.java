package pe.dcs.app.features.finance;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.dcs.app.entity.FinancialCashRegister;
import pe.dcs.app.entity.Person;
import pe.dcs.app.features.finance.response.FinancialCashRegisterResponse;
import pe.dcs.app.util.auditable.BaseMapper;

@Component
@RequiredArgsConstructor
public class FinancialCashRegisterMapper {

    private final FinancialCashRegisterAccessGuard accessGuard;

    public FinancialCashRegisterResponse simple(FinancialCashRegister register, boolean showAudit) {

        FinancialCashRegisterResponse response = new FinancialCashRegisterResponse();

        BaseMapper.mapAudit(register, response, showAudit);

        response.setId(register.getId());
        response.setOrganizationId(register.getOrganization().getId());

        response.setBranchId(register.getBranch().getId());
        response.setBranchName(register.getBranch().getName());

        response.setRegisterDate(register.getRegisterDate());

        response.setOpeningBalance(register.getOpeningBalance());
        response.setClosingBalance(register.getClosingBalance());
        response.setExpectedBalance(register.getExpectedBalance());
        response.setDifference(register.getDifference());

        response.setStatus(register.getStatus().name());

        if (register.getOpenedByUser() != null) {
            response.setOpenedByUserId(register.getOpenedByUser().getId());
            response.setOpenedByUserName(buildFullName(register.getOpenedByUser()));
        }

        response.setOpenedAt(register.getOpenedAt());

        if (register.getClosedByUser() != null) {
            response.setClosedByUserId(register.getClosedByUser().getId());
            response.setClosedByUserName(buildFullName(register.getClosedByUser()));
        }

        response.setClosedAt(register.getClosedAt());
        response.setNotes(register.getNotes());

        response.setCanManage(accessGuard.canClose(register));

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
