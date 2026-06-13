package pe.dcs.app.security.service.credentials;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import pe.dcs.app.entity.Credential;
import pe.dcs.app.entity.User;
import pe.dcs.app.repository.CredentialRepository;
import pe.dcs.app.repository.UserRepository;

import java.util.List;
import java.util.UUID;

@Service
public class CredentialDetailsService implements UserDetailsService {

    private final CredentialRepository credentialRepository;
    private final UserRepository userRepository;

    public CredentialDetailsService(
            CredentialRepository credentialRepository,
            UserRepository userRepository
    ) {
        this.credentialRepository = credentialRepository;
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        Credential credential =
                credentialRepository.findByUsername(username)
                        .orElseThrow(() ->
                                new UsernameNotFoundException("Credencial no encontrada")
                        );

        User user = userRepository.findByIdWithRole(credential.getUser().getId())
                .orElseThrow(() ->
                        new UsernameNotFoundException("Usuario no encontrado")
                );

        String roleValue = user.getRole().getValue();

        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority(roleValue)
        );

        UUID organizationId =
                user.getOrganization() != null
                        ? user.getOrganization().getId()
                        : null;

        return new CredentialDetailsImpl(
                credential.getId(),
                user.getId(),
                organizationId,
                credential.getUsername(),
                credential.getPassword(),
                user.getName(),
                user.getLastname(),
                true,
                authorities
        );
    }

    private CredentialDetailsImpl getPrincipal() {
        return (CredentialDetailsImpl) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}