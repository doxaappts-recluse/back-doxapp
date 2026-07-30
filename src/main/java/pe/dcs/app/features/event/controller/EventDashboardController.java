package pe.dcs.app.features.event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.event.response.dashboard.EventDashboardResponse;
import pe.dcs.app.features.event.service.EventDashboardService;
import pe.dcs.app.util.ApiResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events-dashboard")
@RequiredArgsConstructor
public class EventDashboardController {

    private final EventDashboardService dashboardService;

    @GetMapping("/get/{eventId}")
    public ApiResponse<EventDashboardResponse> getDashboard(
            @PathVariable UUID eventId
    ) {
        return new ApiResponse<>(
                200,
                "Dashboard fetched successfully",
                dashboardService.getDashboard(eventId)
        );
    }
}