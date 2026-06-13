package pe.dcs.app.features.user.access_user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.*;
import pe.dcs.app.features.user.access_user.request.UserCredentialsRequest;
import pe.dcs.app.repository.*;
import pe.dcs.app.features.user.access_user.mapper.AccessUserMapper;
import pe.dcs.app.features.user.access_user.request.AccessUserFilterRequest;
import pe.dcs.app.features.user.access_user.request.AccessUserSearchRequest;
import pe.dcs.app.features.user.access_user.response.AccessUserSearchResponse;
import pe.dcs.app.features.user.access_user.service.AccessUserSearchService;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.security.service.credentials.CredentialDetailsImpl;
import pe.dcs.app.service.AuthorizationService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.contract.ContractStatus;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccessUserSearchServiceImpl implements AccessUserSearchService {

    private final UserRepository userRepository;
    private final AuthContext authContext;
    private final AuthorizationService authorizationService;
    private final CredentialRepository credentialRepository;
    private final ContractRepository contractRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;

    @Override
    public PageResponse<AccessUserSearchResponse> search(
            AccessUserSearchRequest request
    ) {

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        UUID organizationId =
                authContext.getOrganizationId();

        AccessUserFilterRequest filters =
                request.getFilters();

        Page<User> page =
                userRepository.findAll(
                        AccessUserSpecification.filter(
                                organizationId,

                                // base filters
                                filters != null
                                        ? filters.getName()
                                        : null,

                                filters != null
                                        ? filters.getLastname()
                                        : null,

                                // access filters
                                filters != null
                                        ? filters.getHasCredential()
                                        : null,

                                filters != null
                                        ? filters.getCredentialActive()
                                        : null,

                                filters != null
                                        ? filters.getUsername()
                                        : null,

                                filters != null
                                        ? filters.getRole()
                                        : null
                        ),
                        pageable
                );

        List<AccessUserSearchResponse> content =
                page.getContent()
                        .stream()
                        .map(AccessUserMapper::map)
                        .toList();

        return new PageResponse<>(
                content,
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    //********************** GLOBAL USER **********************//
    // =========================================================
    // ENABLE
    // =========================================================

    @Override
    public void enable(UUID userId) {

        User target = getUser(userId);

        Credential credential =
                getCredentialOrThrow(target);

        authorizationService.assertCanAccessUser(
                authContext.getPrincipal(),
                toCredentialDetails(target)
        );

        // ya activo
        if (credential.getStatus() == StatusType.ACTIVE) {
            return;
        }

        UUID organizationId =
                target.getOrganization().getId();

        Contract activeContract =
                getActiveContractOrThrow(
                        organizationId
                );

        long activeUsers =
                credentialRepository
                        .countByUserOrganizationIdAndStatus(
                                organizationId,
                                StatusType.ACTIVE
                        );

        if (activeUsers >= activeContract.getNumberUsers() + 1) {
            throw new Exceptions(
                    "Maximum active users reached for contract",
                    HttpStatus.CONFLICT
            );
        }

        credential.setStatus(StatusType.ACTIVE);

        credentialRepository.save(credential);
    }

    // =========================================================
    // DISABLE
    // =========================================================

    @Override
    public void disable(UUID userId) {

        User target = getUser(userId);

        Credential credential =
                getCredentialOrThrow(target);

        authorizationService.assertCanAccessUser(
                authContext.getPrincipal(),
                toCredentialDetails(target)
        );

        if (credential.getStatus() == StatusType.INACTIVE) {
            return;
        }

        credential.setStatus(StatusType.INACTIVE);

        credentialRepository.save(credential);
    }

    // =========================================================
    // GET USER
    // =========================================================

    private User getUser(UUID id) {

        return userRepository.findById(id)
                .orElseThrow(() ->
                        new Exceptions(
                                "User not found",
                                HttpStatus.NOT_FOUND
                        )
                );
    }

    private Credential getCredentialOrThrow(
            User user
    ) {

        if (user.getCredential() == null) {
            throw new Exceptions(
                    "User has no credential",
                    HttpStatus.CONFLICT
            );
        }

        return user.getCredential();
    }

    // =========================================================
    // TARGET USER -> SECURITY MODEL
    // =========================================================

    private CredentialDetailsImpl toCredentialDetails(User user) {

        return new CredentialDetailsImpl(
                user.getCredential().getId(),
                user.getId(),
                user.getOrganization() != null
                        ? user.getOrganization().getId()
                        : null,
                user.getCredential().getUsername(),
                user.getCredential().getPassword(),
                user.getName(),
                user.getLastname(),
                user.getCredential().getStatus() == StatusType.ACTIVE,
                List.of(() -> user.getRole().getValue())
        );
    }

    private CredentialDetailsImpl toWithoutCredentialDetails(User user) {

        Credential credential = user.getCredential();
        Role role = user.getRole();

        Collection<? extends GrantedAuthority> authorities =
                role != null
                        ? List.of(
                        (GrantedAuthority) role::getValue
                )
                        : List.of();

        return new CredentialDetailsImpl(
                credential != null
                        ? credential.getId()
                        : null,

                user.getId(),

                user.getOrganization() != null
                        ? user.getOrganization().getId()
                        : null,

                credential != null
                        ? credential.getUsername()
                        : null,

                credential != null
                        ? credential.getPassword()
                        : null,

                user.getName(),
                user.getLastname(),

                credential != null
                        && credential.getStatus() == StatusType.ACTIVE,

                authorities
        );
    }

    private Contract getActiveContractOrThrow(
            UUID organizationId
    ) {

        LocalDate today = LocalDate.now();

        return contractRepository
                .findTopByOrganizationIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByEndDateDesc(
                        organizationId,
                        ContractStatus.ACTIVE,
                        today,
                        today
                )
                .orElseThrow(() ->
                        new Exceptions(
                                "No active contract found",
                                HttpStatus.CONFLICT
                        )
                );
    }

    // =========================================================
    // CHANGE PASSWORD
    // =========================================================

    @Override
    @Transactional
    public void changePassword(
            UUID id,
            UserCredentialsRequest request
    ) {

        // 1. USER EXISTS
        User target = getUser(id);

        // 2. SECURITY
        authorizationService.assertCanAccessUser(
                authContext.getPrincipal(),
                toWithoutCredentialDetails(target)
        );

        UUID organizationId =
                authContext.getOrganizationId();

        Credential credential =
                target.getCredential();

        // =========================================
        // FIRST ACTIVATION
        // =========================================
        if (credential == null) {

            Contract contract =
                    contractRepository
                            .findFirstByOrganizationIdAndStatusOrderByStartDateDesc(
                                    organizationId,
                                    ContractStatus.ACTIVE
                            )
                            .orElseThrow(() ->
                                    new Exceptions(
                                            "No active contract",
                                            HttpStatus.BAD_REQUEST
                                    )
                            );

            long activeUsers =
                    credentialRepository
                            .countByUser_Organization_IdAndStatus(
                                    organizationId,
                                    StatusType.ACTIVE
                            );

            if (activeUsers >= contract.getNumberUsers() + 1) {
                throw new Exceptions(
                        "Maximum number of active users reached",
                        HttpStatus.CONFLICT
                );
            }

            if (credentialRepository.existsByUsername(
                    request.getUsername()
            )) {
                throw new Exceptions(
                        "Username already exists",
                        HttpStatus.CONFLICT
                );
            }

            Role role = roleRepository
                    .findByValue("ORG_USER")
                    .orElseThrow(() ->
                            new Exceptions(
                                    "Role ORG_USER not found",
                                    HttpStatus.INTERNAL_SERVER_ERROR
                            )
                    );

            credential = new Credential();
            credential.setUser(target);
            credential.setStatus(StatusType.ACTIVE);

            target.setRole(role);
            target.setCredential(credential);
        }

        // =========================================
        // UPDATE EXISTING
        // =========================================
        else {

            boolean usernameExists =
                    credentialRepository
                            .existsByUsernameAndIdNot(
                                    request.getUsername(),
                                    credential.getId()
                            );

            if (usernameExists) {
                throw new Exceptions(
                        "Username already exists",
                        HttpStatus.CONFLICT
                );
            }
        }

        credential.setUsername(
                request.getUsername()
        );

        credential.setPassword(
                passwordEncoder.encode(
                        request.getPassword()
                )
        );

        credentialRepository.save(credential);
    }

}