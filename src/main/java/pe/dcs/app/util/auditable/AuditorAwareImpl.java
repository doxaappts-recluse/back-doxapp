package pe.dcs.app.util.auditable;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import pe.dcs.app.entity.User;
import pe.dcs.app.repository.UserRepository;
import pe.dcs.app.security.service.credentials.CredentialDetailsImpl;

import java.util.Optional;

@Component("auditorAware")
@RequiredArgsConstructor
public class AuditorAwareImpl
        implements AuditorAware<User> {

    private final UserRepository userRepository;

    @Override
    public Optional<User> getCurrentAuditor() {

        Authentication auth =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        if (auth == null
                || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        CredentialDetailsImpl principal =
                (CredentialDetailsImpl) auth.getPrincipal();

        return userRepository.findById(
                principal.getUserId()
        );
    }
}