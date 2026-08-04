package pe.dcs.app.features.event.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Event;
import pe.dcs.app.features.event.response.dashboard.*;
import pe.dcs.app.features.event.service.EventDashboardService;
import pe.dcs.app.repository.EventFinanceRepository;
import pe.dcs.app.repository.EventRegistrationRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.enums.events.EventFinanceStatus;
import pe.dcs.app.util.enums.events.EventFinanceType;
import pe.dcs.app.util.enums.events.EventStatus;
import pe.dcs.app.util.enums.events.RegistrationStatus;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventDashboardServiceImpl implements EventDashboardService {

    private final EventFinanceRepository financeRepository;
    private final EventRegistrationRepository registrationRepository;
    private final AuthContext authContext;
    private final EventAccessGuard eventAccessGuard;

    /**
     * Dashboard es de gestión exclusiva de ORG_ADMIN/ORG_BRANCH_ADMIN
     * de la organización del contexto actual. SYSTEM queda
     * explícitamente fuera (igual que el resto de Eventos).
     */
    private void assertCallerCanManage() {
        authContext.assertCanManageOrgOrBranchOnlyForCurrent(
                "Solo un administrador de organización o de sede puede ver el dashboard."
        );
    }

    @Override
    @Transactional(readOnly = true)
    public EventDashboardResponse getDashboard(UUID eventId) {

        assertCallerCanManage();

        Event event = eventAccessGuard.assertCanManage(eventId);

        EventDashboardResponse res = new EventDashboardResponse();

        // =========================
        // FINANCE
        // =========================
        FinanceDashboard finance = new FinanceDashboard();

        BigDecimal income =
                safe(financeRepository.sumIncomeApproved(eventId));

        BigDecimal expense =
                safe(financeRepository.sumExpenseApproved(eventId));

        BigDecimal pendingIncome =
                safe(financeRepository.sumByStatusAndType(eventId, EventFinanceStatus.PENDING, EventFinanceType.INCOME));

        BigDecimal pendingExpense =
                safe(financeRepository.sumByStatusAndType(eventId, EventFinanceStatus.PENDING, EventFinanceType.EXPENSE));

        BigDecimal balance = income.subtract(expense);

        finance.setTotalIncome(income);
        finance.setTotalExpense(expense);
        finance.setPendingIncome(pendingIncome);
        finance.setPendingExpense(pendingExpense);
        finance.setBalance(balance);

        res.setFinance(finance);

        // =========================
        // REGISTRATION
        // =========================
        RegistrationDashboard reg = new RegistrationDashboard();

        long total =
                registrationRepository.countByEventId(eventId);

        long cancelled =
                registrationRepository.countByEventIdAndStatus(
                        eventId,
                        RegistrationStatus.CANCELLED
                );

        long active =
                registrationRepository.countByEventIdAndStatus(
                        eventId,
                        RegistrationStatus.REGISTERED
                );

        int capacity = event.getCapacity() != null ? event.getCapacity() : 0;

        double occupancy =
                capacity > 0
                        ? (active * 100.0) / capacity
                        : 0;

        BigDecimal registrationIncome =
                safe(registrationRepository.sumRegistrationIncome(eventId));

        reg.setTotalRegistrations(total);
        reg.setTotalCancelled(cancelled);
        reg.setTotalActive(active);
        reg.setOccupancyRate(occupancy);
        reg.setRegistrationIncome(registrationIncome);

        res.setRegistration(reg);

        // =========================
        // ALERTS
        // =========================
        AlertsDashboard alerts = new AlertsDashboard();

        /*
         * "Sobre presupuesto" debe medirse contra el presupuesto
         * planeado del evento (expectedBudget), no contra el
         * ingreso. Comparar expense > income es matemáticamente
         * idéntico a negativeBalance (balance = income - expense),
         * así que ambas alertas siempre se disparaban juntas de
         * forma redundante. Sin expectedBudget definido no hay
         * forma de estar "sobre presupuesto".
         */
        BigDecimal expectedBudget = event.getExpectedBudget();

        boolean overBudget =
                expectedBudget != null
                        && expense.compareTo(expectedBudget) > 0;

        /*
         * Aviso temprano: ya se usó el 80% del presupuesto pero
         * todavía no se pasó. Igual idea que nearCapacity para la
         * ocupación — avisar ANTES de llegar al límite, no solo
         * cuando ya se cruzó.
         */
        boolean nearBudget =
                expectedBudget != null
                        && !overBudget
                        && expense.compareTo(
                                expectedBudget.multiply(new BigDecimal("0.8"))
                        ) >= 0;

        boolean negativeBalance = balance.compareTo(BigDecimal.ZERO) < 0;
        boolean nearCapacity = occupancy >= 80;

        /*
         * Un evento recién creado (DRAFT) o CANCELLED todavía no
         * tiene por qué tener ingresos ni movimientos financieros —
         * eso no es una alerta real, es el estado normal antes de
         * publicarlo. Estas dos señales solo importan una vez que
         * el evento ya está en marcha (PUBLISHED/FINISHED).
         */
        boolean eventInProgress =
                event.getStatus() == EventStatus.PUBLISHED
                        || event.getStatus() == EventStatus.FINISHED;

        boolean noIncome =
                eventInProgress
                        && income.compareTo(BigDecimal.ZERO) == 0;

        alerts.setOverBudget(overBudget);
        alerts.setNearBudget(nearBudget);
        alerts.setNegativeBalance(negativeBalance);
        alerts.setNearCapacity(nearCapacity);
        alerts.setNoIncome(noIncome);

        res.setAlerts(alerts);

        // =========================
        // NOTIFICATIONS
        // =========================
        NotificationsDashboard notifications = new NotificationsDashboard();

        long financeCount =
                financeRepository.countByEventId(eventId);

        /*
         * Con muestras muy chicas (p.ej. 1 inscripción cancelada de
         * 1 total = 100%) la tasa de cancelación no es una señal
         * confiable. Se exige un mínimo de inscripciones antes de
         * evaluarla.
         */
        boolean hasEnoughRegistrations = total >= 5;

        double cancelRate =
                total == 0 ? 0 : (cancelled * 100.0) / total;

        boolean pendingApprovals =
                pendingIncome.add(pendingExpense)
                        .compareTo(BigDecimal.ZERO) > 0;

        notifications.setNoFinancialMovements(
                eventInProgress && financeCount == 0
        );

        notifications.setHighCancellationRate(
                hasEnoughRegistrations && cancelRate >= 30
        );

        notifications.setPendingApprovals(pendingApprovals);

        res.setNotifications(notifications);

        return res;
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}