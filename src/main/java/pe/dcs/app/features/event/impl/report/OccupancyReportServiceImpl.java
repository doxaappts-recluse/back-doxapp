package pe.dcs.app.features.event.impl.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Event;
import pe.dcs.app.features.event.response.reports.OccupancyReportResponse;
import pe.dcs.app.features.event.service.OccupancyReportService;
import pe.dcs.app.repository.EventRegistrationRepository;
import pe.dcs.app.repository.EventRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OccupancyReportServiceImpl implements OccupancyReportService {

    private final EventRegistrationRepository repository;
    private final EventRepository eventRepository;

    @Override
    @Transactional(readOnly = true)
    public List<OccupancyReportResponse> get(UUID eventId) {

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        int capacity = event.getCapacity() != null ? event.getCapacity() : 0;

        return repository.occupancyReport(eventId)
                .stream()
                .map(r -> {
                    long registered = ((Number) r[1]).longValue();
                    OccupancyReportResponse dto = new OccupancyReportResponse();
                    dto.setDate(((java.sql.Date) r[0]).toLocalDate());
                    dto.setRegistered(registered);
                    dto.setOccupancyRate(
                            capacity > 0 ? (registered * 100.0) / capacity : 0
                    );

                    return dto;
                })
                .toList();
    }
}