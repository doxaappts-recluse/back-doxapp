package pe.dcs.app.features.event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.event.impl.EventAccessGuard;
import pe.dcs.app.features.event.response.reports.AgeReportResponse;
import pe.dcs.app.features.event.response.reports.BranchReportResponse;
import pe.dcs.app.features.event.response.reports.CategoryReportResponse;
import pe.dcs.app.features.event.response.reports.FinanceReportResponse;
import pe.dcs.app.features.event.response.reports.OccupancyReportResponse;
import pe.dcs.app.features.event.response.reports.PaymentStatusReportResponse;
import pe.dcs.app.features.event.response.reports.RegistrationReportResponse;
import pe.dcs.app.features.event.service.AgeReportService;
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
    private final AgeReportService ageReportService;

    /**
     * Los reportes muestran información interna del evento
     * (montos, asistentes, edades): quedan reservados al org
     * admin o a la sede coordinadora, sin importar el scope. Ver
     * EventAccessGuard.
     */
    private final EventAccessGuard eventAccessGuard;

    @GetMapping("/registrations/{eventId}")
    public ApiResponse<List<RegistrationReportResponse>> getRegistration(@PathVariable UUID eventId) {

        eventAccessGuard.assertCanManage(eventId);

        return new ApiResponse<>(
                200,
                "success.reporteInscripcionesObtenidoCorrectamente",
                registrationReportService.get(eventId)
        );
    }


    @GetMapping("/registrations/category/{eventId}")
    public ApiResponse<List<CategoryReportResponse>> getCategoryBreakdown(@PathVariable UUID eventId) {

        eventAccessGuard.assertCanManage(eventId);

        return new ApiResponse<>(
                200,
                "success.mezclaCategoriasObtenidaCorrectamente",
                registrationReportService.getCategoryBreakdown(eventId)
        );
    }

    @GetMapping("/registrations/payment-status/{eventId}")
    public ApiResponse<List<PaymentStatusReportResponse>> getPaymentStatusBreakdown(@PathVariable UUID eventId) {

        eventAccessGuard.assertCanManage(eventId);

        return new ApiResponse<>(
                200,
                "success.desglosePorEstadoPagoObtenidoCorrectamente",
                registrationReportService.getPaymentStatusBreakdown(eventId)
        );
    }

    @GetMapping("/registrations/branch/{eventId}")
    public ApiResponse<List<BranchReportResponse>> getBranchBreakdown(@PathVariable UUID eventId) {

        eventAccessGuard.assertCanManage(eventId);

        return new ApiResponse<>(
                200,
                "success.desglosePorSedeObtenidoCorrectamente",
                registrationReportService.getBranchBreakdown(eventId)
        );
    }

    @GetMapping("/finance/{eventId}")
    public ApiResponse<List<FinanceReportResponse>> getFinance(@PathVariable UUID eventId) {

        eventAccessGuard.assertCanManage(eventId);

        return new ApiResponse<>(
                200,
                "success.reporteFinanzasObtenidoCorrectamente",
                financeReportService.get(eventId)
        );
    }

    @GetMapping("/occupancy/{eventId}")
    public ApiResponse<List<OccupancyReportResponse>> getOccupancy(@PathVariable UUID eventId) {

        eventAccessGuard.assertCanManage(eventId);

        return new ApiResponse<>(
                200,
                "success.reporteFinanzasObtenidoCorrectamente",
                occupancyReportService.get(eventId)
        );
    }

    @GetMapping("/age-report/{eventId}")
    public ApiResponse<List<AgeReportResponse>> ageReport(
            @PathVariable UUID eventId
    ) {

        eventAccessGuard.assertCanManage(eventId);

        return new ApiResponse<>(
                200,
                "success.reporteEdadesObtenidoCorrectamente",
                ageReportService.get(eventId)
        );

    }
}