package pe.dcs.app.features.module;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.module.response.MeAccessResponse;
import pe.dcs.app.features.module.service.SidebarService;
import pe.dcs.app.util.ApiResponse;

@RestController
@RequestMapping("/api/v1/sidebar")
@RequiredArgsConstructor
public class SidebarController {

    private final SidebarService sidebarService;

    @GetMapping
    public ResponseEntity<ApiResponse<MeAccessResponse>> getSidebar(
            Authentication authentication
    ) {

        MeAccessResponse response =
                sidebarService.getSidebar(authentication);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Sidebar fetched successfully",
                        response
                )
        );
    }
}