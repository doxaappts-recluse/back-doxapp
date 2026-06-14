package pe.dcs.app.features.profile;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import pe.dcs.app.entity.User;
import pe.dcs.app.features.profile.mapper.ProfileMapper;
import pe.dcs.app.features.profile.response.ProfileResponse;
import pe.dcs.app.features.profile.service.ProfileService;
import pe.dcs.app.repository.UserRepository;
import pe.dcs.app.util.Exceptions;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserRepository userRepository;
    private final ProfileMapper profileMapper;

    @Override
    public ProfileResponse getProfile() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String username = authentication.getName();

        User user = userRepository.findProfileByUsername(username)
                .orElseThrow(() ->
                        new Exceptions(
                                "Perfil no encontrado",
                                HttpStatus.NOT_FOUND
                        )
                );

        return profileMapper.toResponse(user);
    }
}