package pe.dcs.app.features.profile.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Credential;
import pe.dcs.app.entity.User;
import pe.dcs.app.entity.UserAccess;
import pe.dcs.app.features.profile.response.ProfileAccessResponse;
import pe.dcs.app.features.profile.response.ProfileResponse;

import java.util.List;

@Component
public class ProfileMapper {

    public ProfileResponse toResponse(User user) {

        Credential credential = user.getCredential();

        List<ProfileAccessResponse> accesses =
                user.getAccesses()
                        .stream()
                        .filter(UserAccess::getActive)
                        .map(access ->
                                new ProfileAccessResponse(

                                        access.getOrganization() != null
                                                ? access.getOrganization().getName()
                                                : null,

                                        access.getBranch() != null
                                                ? access.getBranch().getName()
                                                : "Todas las sedes",

                                        access.getRole() != null
                                                ? access.getRole().getName()
                                                : null
                                )
                        )
                        .toList();

        return new ProfileResponse(
                user.getId(),
                credential != null
                        ? credential.getUsername()
                        : null,
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
                accesses,
                credential != null
                        ? credential.getStatus()
                        : null,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}