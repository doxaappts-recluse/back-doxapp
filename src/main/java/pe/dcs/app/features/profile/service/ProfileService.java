package pe.dcs.app.features.profile.service;

import pe.dcs.app.features.profile.request.ProfileChangePasswordRequest;
import pe.dcs.app.features.profile.request.ProfileUpdateContactRequest;
import pe.dcs.app.features.profile.response.ProfileResponse;

public interface ProfileService {
    ProfileResponse getProfile();

    ProfileResponse updateContact(ProfileUpdateContactRequest request);

    void changePassword(ProfileChangePasswordRequest request);
}