package pe.dcs.app.features.event.impl.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.event.response.reports.RegistrationReportResponse;
import pe.dcs.app.features.event.service.RegistrationReportService;
import pe.dcs.app.repository.EventRegistrationRepository;

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
}