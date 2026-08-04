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
import java.util.ArrayList;
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

        List<Object[]> rows = repository.occupancyReport(eventId);

        List<OccupancyReportResponse> result = new ArrayList<>();

        /*
         * La ocupación es acumulada, no del día: "registered" acá
         * es el neto de inscripciones activas a la fecha (altas
         * menos cancelaciones desde el inicio), no el conteo de
         * ESE día — un gráfico de "ocupación diaria" no dice nada
         * útil, lo que importa es cuánto del aforo se ha ido
         * llenando con el tiempo.
         */
        long cumulativeActive = 0;

        for (Object[] r : rows) {

            long registeredDay = ((Number) r[1]).longValue();
            long cancelledDay = ((Number) r[2]).longValue();

            cumulativeActive += (registeredDay - cancelledDay);

            OccupancyReportResponse dto = new OccupancyReportResponse();
            dto.setDate(((java.sql.Date) r[0]).toLocalDate());
            dto.setRegistered(cumulativeActive);
            dto.setOccupancyRate(
                    capacity > 0 ? (cumulativeActive * 100.0) / capacity : 0
            );

            result.add(dto);
        }

        return result;
    }
}