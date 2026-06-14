package pe.dcs.app.features.profile;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.profile.response.ProfileResponse;
import pe.dcs.app.features.profile.service.ProfileService;
import pe.dcs.app.util.ApiResponse;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/me")
    public ApiResponse<ProfileResponse> getProfile() {

        return new ApiResponse<>(
                200,
                "Profile fetched successfully",
                profileService.getProfile()
        );
    }
}