package pe.dcs.app.features.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.dashboard.response.DashboardHomeResponse;
import pe.dcs.app.util.ApiResponse;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/home")
    public ApiResponse<DashboardHomeResponse> getHome() {
        return new ApiResponse<>(
                200,
                "success.dashboardFetchedSuccessfully",
                dashboardService.getHome()
        );
    }
}
