package pe.dcs.app.features.profile;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Credential;
import pe.dcs.app.entity.Person;
import pe.dcs.app.features.profile.mapper.ProfileMapper;
import pe.dcs.app.features.profile.request.ProfileChangePasswordRequest;
import pe.dcs.app.features.profile.request.ProfileUpdateContactRequest;
import pe.dcs.app.features.profile.response.ProfileResponse;
import pe.dcs.app.features.profile.service.ProfileService;
import pe.dcs.app.repository.CredentialRepository;
import pe.dcs.app.repository.PersonRepository;
import pe.dcs.app.util.Exceptions;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final PersonRepository userRepository;
    private final CredentialRepository credentialRepository;
    private final ProfileMapper profileMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * Resuelve la Person del usuario autenticado a partir del
     * username en el SecurityContext — mismo criterio en
     * getProfile/updateContact/changePassword, todos operan
     * siempre sobre "quien está logueado", nunca sobre un id
     * recibido por parámetro (a diferencia de AccessUserServiceImpl,
     * que es un admin gestionando a otra persona).
     */
    private Person getCurrentPerson() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String username = authentication.getName();

        return userRepository.findProfileByUsername(username)
                .orElseThrow(() ->
                        new Exceptions(
                                "error.perfilNoEncontrado",
                                HttpStatus.NOT_FOUND
                        )
                );
    }

    @Override
    public ProfileResponse getProfile() {
        return profileMapper.toResponse(getCurrentPerson());
    }

    @Override
    @Transactional
    public ProfileResponse updateContact(ProfileUpdateContactRequest request) {

        Person user = getCurrentPerson();

        user.setPhone(request.getPhone());
        user.setAddress(request.getAddress());

        return profileMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void changePassword(ProfileChangePasswordRequest request) {

        Person user = getCurrentPerson();

        Credential credential = user.getCredential();

        if (credential == null) {
            throw new Exceptions(
                    "error.usuarioNoTieneCredencial",
                    HttpStatus.CONFLICT
            );
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), credential.getPassword())) {
            throw new Exceptions(
                    "error.contrasenaActualIncorrecta",
                    HttpStatus.BAD_REQUEST
            );
        }

        credential.setPassword(
                passwordEncoder.encode(request.getNewPassword())
        );

        credentialRepository.save(credential);
    }
}