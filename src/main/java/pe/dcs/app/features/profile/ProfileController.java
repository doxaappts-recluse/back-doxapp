package pe.dcs.app.features.profile;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.profile.request.ProfileChangePasswordRequest;
import pe.dcs.app.features.profile.request.ProfileUpdateContactRequest;
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
                "success.profileFetchedSuccessfully",
                profileService.getProfile()
        );
    }

    @PatchMapping("/contact")
    public ApiResponse<ProfileResponse> updateContact(
            @RequestBody ProfileUpdateContactRequest request
    ) {

        return new ApiResponse<>(
                200,
                "success.perfilActualizadoCorrectamente",
                profileService.updateContact(request)
        );
    }

    @PatchMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody ProfileChangePasswordRequest request
    ) {

        profileService.changePassword(request);

        return new ApiResponse<>(
                200,
                "success.contrasenaActualizadaCorrectamente",
                null
        );
    }
}