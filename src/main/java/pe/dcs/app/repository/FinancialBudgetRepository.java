package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.FinancialBudget;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

@Repository
public interface FinancialBudgetRepository extends JpaRepository<FinancialBudget, UUID> {

    List<FinancialBudget> findByOrganizationIdOrderByPeriodYearDescPeriodMonthDescNameAsc(
            UUID organizationId
    );

    /**
     * Presupuestos ACTIVOS de una organización para un período
     * puntual — usado por FinancialBudgetServiceImpl.progress() y,
     * más adelante, por el dashboard de finanzas para mostrar el
     * avance de todos los presupuestos vigentes de un mes.
     */
    List<FinancialBudget> findByOrganizationIdAndStatusAndPeriodYearAndPeriodMonth(
            UUID organizationId,
            StatusType status,
            Integer periodYear,
            Integer periodMonth
    );
}
