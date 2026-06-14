package pe.dcs.app.features.profile.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.User;
import pe.dcs.app.features.profile.response.ProfileResponse;

@Component
public class ProfileMapper {

    public ProfileResponse toResponse(User user) {

        return new ProfileResponse(
                user.getId(),
                user.getCredential().getUsername(),
                user.getName(),
                user.getLastname(),
                user.getDni(),
                user.getPhone(),
                user.getAddress(),
                user.getSex(),
                user.getDateBirth(),
                user.getMaritalStatus(),
                user.getChildren(),
                user.getDateAdmission(),
                user.getRole() != null ? user.getRole().getName() : null,
                user.getOrganization() != null
                        ? user.getOrganization().getName()
                        : null,
                user.getCredential().getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}