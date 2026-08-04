package pe.dcs.app.features.event.service;

import pe.dcs.app.features.event.response.reports.BranchReportResponse;
import pe.dcs.app.features.event.response.reports.CategoryReportResponse;
import pe.dcs.app.features.event.response.reports.PaymentStatusReportResponse;
import pe.dcs.app.features.event.response.reports.RegistrationReportResponse;

import java.util.List;
import java.util.UUID;

public interface RegistrationReportService {
    List<RegistrationReportResponse> get(UUID eventId);
    List<CategoryReportResponse> getCategoryBreakdown(UUID eventId);
    List<PaymentStatusReportResponse> getPaymentStatusBreakdown(UUID eventId);
    List<BranchReportResponse> getBranchBreakdown(UUID eventId);
}