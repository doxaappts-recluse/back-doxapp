package pe.dcs.app.features.event.impl.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.event.response.reports.BranchReportResponse;
import pe.dcs.app.features.event.response.reports.CategoryReportResponse;
import pe.dcs.app.features.event.response.reports.PaymentStatusReportResponse;
import pe.dcs.app.features.event.response.reports.RegistrationReportResponse;
import pe.dcs.app.features.event.service.RegistrationReportService;
import pe.dcs.app.repository.EventRegistrationRepository;
import pe.dcs.app.util.enums.events.PaymentStatus;
import pe.dcs.app.util.enums.events.RegistrationCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegistrationReportServiceImpl implements RegistrationReportService {

    private final EventRegistrationRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<RegistrationReportResponse> get(UUID eventId) {

        return repository.registrationReport(eventId)
                .stream()
                .map(r -> {
                    RegistrationReportResponse dto = new RegistrationReportResponse();
                    dto.setDate(((java.sql.Date) r[0]).toLocalDate());
                    dto.setRegistered(((Number) r[1]).longValue());
                    dto.setCancelled(((Number) r[2]).longValue());
                    return dto;
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryReportResponse> getCategoryBreakdown(UUID eventId) {

        return repository.categoryReport(eventId)
                .stream()
                .map(r -> {
                    CategoryReportResponse dto = new CategoryReportResponse();
                    dto.setCategory(((RegistrationCategory) r[0]).name());
                    dto.setTotal(((Number) r[1]).longValue());
                    return dto;
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentStatusReportResponse> getPaymentStatusBreakdown(UUID eventId) {

        return repository.paymentStatusReport(eventId)
                .stream()
                .map(r -> {
                    PaymentStatusReportResponse dto = new PaymentStatusReportResponse();
                    dto.setStatus(((PaymentStatus) r[0]).name());
                    dto.setTotal(((Number) r[1]).longValue());
                    dto.setAmount(
                            r[2] != null
                                    ? new BigDecimal(r[2].toString())
                                    : BigDecimal.ZERO
                    );
                    return dto;
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchReportResponse> getBranchBreakdown(UUID eventId) {

        return repository.branchReport(eventId)
                .stream()
                .map(r -> {
                    BranchReportResponse dto = new BranchReportResponse();
                    dto.setBranchId((UUID) r[0]);
                    dto.setBranchName((String) r[1]);
                    dto.setTotal(((Number) r[2]).longValue());
                    dto.setAmount(
                            r[3] != null
                                    ? new BigDecimal(r[3].toString())
                                    : BigDecimal.ZERO
                    );
                    return dto;
                })
                .toList();
    }
}