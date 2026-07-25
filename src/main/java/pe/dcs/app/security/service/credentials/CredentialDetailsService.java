package pe.dcs.app.security.service.credentials;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Credential;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.UserAccess;
import pe.dcs.app.repository.CredentialRepository;
import pe.dcs.app.security.service.UserAccessContext;
import pe.dcs.app.util.enums.StatusType;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class CredentialDetailsService implements UserDetailsService {

    private final CredentialRepository credentialRepository;

    /**
     * Login inicial.
     *
     * Todavía no existe contexto seleccionado.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(
            String username
    ) throws UsernameNotFoundException {
        return loadUserByUsername(
                username,
                null,
                null
        );
    }

    /**
     * Reconstrucción desde JWT.
     *
     * Lee:
     *
     * organizationId
     * branchId
     *
     * del token contextual.
     */
    @Transactional(readOnly = true)
    public CredentialDetailsImpl loadUserByUsername(
            String username,
            UUID currentOrganizationId,
            UUID currentBranchId
    ){

        Credential credential =
                credentialRepository
                        .findFullAccessByUsername(username)
                        .orElseThrow(
                                () ->
                                        new UsernameNotFoundException(
                                                "Credential not found"
                                        )
                        );

        Person user =
                credential.getPerson();

        List<UserAccessContext> accesses =
                user.getAccesses()
                        .stream()

                        .filter(
                                a -> a.getActive() == StatusType.ACTIVE
                        )
                        .map(access ->
                                new UserAccessContext(
                                        /*
                                         *
                                         * SYSTEM:
                                         *
                                         * organization = null
                                         *
                                         */
                                        access.getOrganization() != null
                                                ?
                                                access.getOrganization()
                                                        .getId()
                                                :
                                                null,
                                        /*
                                         *
                                         * Branch:
                                         *
                                         * puede ser null
                                         *
                                         */

                                        access.getBranch() != null
                                                ?
                                                access.getBranch()
                                                        .getId()
                                                :
                                                null,
                                        access.getRole()
                                                .getValue()
                                )

                        )
                        .toList();

        Collection<GrantedAuthority> authorities =
                accesses.stream()
                        .map(access ->
                                new SimpleGrantedAuthority(
                                        access.roleCode().name()
                                )
                        )
                        .collect(Collectors.toList());

        return new CredentialDetailsImpl(
                credential.getId(),
                user.getId(),
                credential.getUsername(),
                credential.getPassword(),
                user.getName(),
                user.getLastname(),
                credential.canLogin(),
                accesses,
                authorities,
                currentOrganizationId,
                currentBranchId
        );
    }

}