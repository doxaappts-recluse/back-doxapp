package pe.dcs.app.features.event.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Event;
import pe.dcs.app.features.event.response.dashboard.*;
import pe.dcs.app.features.event.service.EventDashboardService;
import pe.dcs.app.repository.EventFinanceRepository;
import pe.dcs.app.repository.EventRegistrationRepository;
import pe.dcs.app.repository.EventRepository;
import pe.dcs.app.util.enums.events.EventFinanceStatus;
import pe.dcs.app.util.enums.events.EventFinanceType;
import pe.dcs.app.util.enums.events.RegistrationStatus;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventDashboardServiceImpl implements EventDashboardService {

    private final EventFinanceRepository financeRepository;
    private final EventRegistrationRepository registrationRepository;
    private final EventRepository eventRepository;

    @Override
    @Transactional(readOnly = true)
    public EventDashboardResponse getDashboard(UUID eventId) {

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

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
                financeRepository.sumByStatusAndType(eventId, EventFinanceStatus.PENDING, EventFinanceType.INCOME);

        BigDecimal pendingExpense =
                financeRepository.sumByStatusAndType(eventId, EventFinanceStatus.PENDING, EventFinanceType.EXPENSE);

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

        reg.setTotalRegistrations(total);
        reg.setTotalCancelled(cancelled);
        reg.setTotalActive(active);
        reg.setOccupancyRate(occupancy);

        res.setRegistration(reg);

        // =========================
        // ALERTS
        // =========================
        AlertsDashboard alerts = new AlertsDashboard();

        boolean overBudget = expense.compareTo(income) > 0;
        boolean negativeBalance = balance.compareTo(BigDecimal.ZERO) < 0;
        boolean nearCapacity = occupancy >= 80;
        boolean noIncome = income.compareTo(BigDecimal.ZERO) == 0;

        alerts.setOverBudget(overBudget);
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

        double cancelRate =
                total == 0 ? 0 : (cancelled * 100.0) / total;

        notifications.setNoFinancialMovements(financeCount == 0);
        notifications.setHighCancellationRate(cancelRate >= 30);

        res.setNotifications(notifications);

        return res;
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}