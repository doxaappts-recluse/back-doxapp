package pe.dcs.app.features.event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.event.response.reports.FinanceReportResponse;
import pe.dcs.app.features.event.response.reports.OccupancyReportResponse;
import pe.dcs.app.features.event.response.reports.RegistrationReportResponse;
import pe.dcs.app.features.event.service.FinanceReportService;
import pe.dcs.app.features.event.service.OccupancyReportService;
import pe.dcs.app.features.event.service.RegistrationReportService;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events-reports")
@RequiredArgsConstructor
public class ReportController {

    private final RegistrationReportService registrationReportService;
    private final FinanceReportService financeReportService;
    private final OccupancyReportService occupancyReportService;

    @GetMapping("/registrations/{eventId}")
    public ApiResponse<List<RegistrationReportResponse>> getRegistration(@PathVariable UUID eventId) {
        return new ApiResponse<>(
                200,
                "Reporte de inscripciones obtenido correctamente",
                registrationReportService.get(eventId)
        );
    }


    @GetMapping("/finance/{eventId}")
    public ApiResponse<List<FinanceReportResponse>> getFinance(@PathVariable UUID eventId) {
        return new ApiResponse<>(
                200,
                "Reporte de finanzas obtenido correctamente",
                financeReportService.get(eventId)
        );
    }

    @GetMapping("/occupancy/{eventId}")
    public ApiResponse<List<OccupancyReportResponse>> getOccupancy(@PathVariable UUID eventId) {
        return new ApiResponse<>(
                200,
                "Reporte de finanzas obtenido correctamente",
                occupancyReportService.get(eventId)
        );
    }
}