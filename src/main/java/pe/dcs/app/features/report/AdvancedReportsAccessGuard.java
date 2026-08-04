package pe.dcs.app.features.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.dcs.app.security.service.AuthContext;

/**
 * Guard del Dashboard Ejecutivo / Reportes Avanzados. Sin
 * delegación a org user y sin bypass SYSTEM (mismo criterio que
 * EventDashboardServiceImpl.assertCallerCanManage): solo org admin
 * (ve toda la organización) o branch admin (ve solo su sede) de la
 * organización/sede del contexto actual.
 */
@Component
@RequiredArgsConstructor
public class AdvancedReportsAccessGuard {

    private final AuthContext authContext;

    public void assertCanUse() {
        authContext.assertCanManageOrgOrBranchOnlyForCurrent(
                "Solo un administrador de organización o de sede puede ver los reportes avanzados."
        );
    }
}
