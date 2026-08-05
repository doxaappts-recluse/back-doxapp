package pe.dcs.app.features.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;

/**
 * Guard del Dashboard de bienvenida. A diferencia de
 * {@link pe.dcs.app.features.report.AdvancedReportsAccessGuard}
 * (exclusivo de org admin/branch admin), este dashboard se abre a
 * TODOS los roles autenticados — ver DashboardServiceImpl.getHome():
 * SYSTEM ve un resumen de plataforma (scope "SYSTEM"), org
 * admin/branch admin ven lo contratado (scope "ORGANIZATION"/
 * "BRANCH"), y org user delegado ve solo lo que tiene asignado
 * (scope "USER").
 */
@Component
@RequiredArgsConstructor
public class DashboardAccessGuard {

    private final AuthContext authContext;

    public void assertCanUse() {

        if (authContext.isSystem()) {
            return;
        }

        if (authContext.getCurrentOrganizationId() == null
                || authContext.getCurrentBranchId() == null) {

            throw new Exceptions(
                    "error.debeTenerOrganizacionSedeSeleccionadasVer",
                    HttpStatus.FORBIDDEN
            );
        }
    }
}
