package pe.dcs.app.features.event.impl.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.event.response.reports.FinanceReportResponse;
import pe.dcs.app.features.event.service.FinanceReportService;
import pe.dcs.app.repository.EventFinanceRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinanceReportServiceImpl implements FinanceReportService {

    private final EventFinanceRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<FinanceReportResponse> get(UUID eventId) {

        return repository.financeReport(eventId)
                .stream()
                .map(r -> {
                    FinanceReportResponse dto = new FinanceReportResponse();
                    dto.setDate(((java.sql.Date) r[0]).toLocalDate());
                    dto.setIncome(r[1] != null ? new BigDecimal(r[1].toString()) : BigDecimal.ZERO);
                    dto.setExpense(r[2] != null ? new BigDecimal(r[2].toString()) : BigDecimal.ZERO);
                    return dto;
                })
                .toList();
    }
}