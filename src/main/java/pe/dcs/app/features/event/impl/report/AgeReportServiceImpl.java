package pe.dcs.app.features.event.impl.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.event.response.reports.AgeReportResponse;
import pe.dcs.app.features.event.service.AgeReportService;
import pe.dcs.app.repository.EventRegistrationRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgeReportServiceImpl
        implements AgeReportService {

    private final EventRegistrationRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<AgeReportResponse> get(UUID eventId) {

        return repository.ageReport(eventId)
                .stream()
                .map(r -> {

                    AgeReportResponse dto =
                            new AgeReportResponse();

                    dto.setRange((String) r[0]);

                    dto.setTotal(
                            ((Number) r[1]).longValue()
                    );

                    return dto;
                })
                .toList();
    }
}
