package pe.dcs.app.features.user.system_user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import pe.dcs.app.entity.Credential;
import pe.dcs.app.entity.Role;
import pe.dcs.app.entity.User;
import pe.dcs.app.repository.OrganizationRepository;
import pe.dcs.app.features.user.system_user.mapper.UserSystemMapper;
import pe.dcs.app.features.user.system_user.request.UserSystemCreateRequest;
import pe.dcs.app.features.user.system_user.request.UserSystemListRequest;
import pe.dcs.app.features.user.system_user.request.UserSystemUpdateRequest;
import pe.dcs.app.features.user.system_user.response.UserSystemResponse;
import pe.dcs.app.features.user.system_user.service.UserSystemService;
import pe.dcs.app.features.user.shared.UserChangePasswordRequest;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;
import pe.dcs.app.repository.CredentialRepository;
import pe.dcs.app.repository.RoleRepository;
import pe.dcs.app.repository.UserRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.security.service.credentials.CredentialDetailsImpl;
import pe.dcs.app.service.AuthorizationService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserSystemServiceImpl implements UserSystemService {

    private final UserRepository repository;
    private final CredentialRepository credentialRepository;
    private final RoleRepository roleRepository;
    private final AuthorizationService authorizationService;
    private final PasswordEncoder passwordEncoder;
    private final AuthContext authContext;

    // =========================================================
    // FIND ALL
    // =========================================================

    @Override
    public PageResponse<UserSystemResponse> findAllSystem(
            UserSystemListRequest request
    ) {

        Pageable pageable = PageableUtil.buildPageable(
                request.getPagination(),
                request.getSorts()
        );

        var page = repository.findAll(
                        UserSystemSpecification.filter(request, "SYSTEM_%"),
                        pageable
                )
                .map(UserSystemMapper::toResponse);

        return new PageResponse<>(
                page.getContent(),
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    @Override
    public PageResponse<UserSystemResponse> findAllOrg(
            UserSystemListRequest request
    ) {

        Pageable pageable = PageableUtil.buildPageable(
                request.getPagination(),
                request.getSorts()
        );

        var page = repository.findAll(
                        UserSystemSpecification.filter(request, "ORG_%"),
                        pageable
                )
                .map(UserSystemMapper::toResponse);

        return new PageResponse<>(
                page.getContent(),
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    // =========================================================
    // FIND BY ID
    // =========================================================

    @Override
    public UserSystemResponse findById(UUID id) {
        return UserSystemMapper.toResponse(getUser(id));
    }

    // =========================================================
    // CREATE
    // =========================================================

    @Override
    public UserSystemResponse create(UserSystemCreateRequest request) {

        boolean exists =
                repository.existsByDniAndOrganizationIsNull(
                        request.getDni()
                );

        if (exists) {
            throw new Exceptions(
                    "System user with this DNI already exists",
                    HttpStatus.CONFLICT
            );
        }

        Role role = roleRepository.findById(request.getRolId())
                .orElseThrow(() ->
                        new Exceptions("Role not found", HttpStatus.NOT_FOUND)
                );

        User user = new User();

        user.setName(request.getName());
        user.setLastname(request.getLastname());
        user.setDni(request.getDni());
        user.setSex(request.getSex());
        user.setPhone(request.getPhone());
        user.setAddress(request.getAddress());
        user.setDateBirth(request.getDateBirth());
        user.setMaritalStatus(request.getMaritalStatus());
        user.setChildren(request.getChildren());
        user.setDateAdmission(request.getDateAdmission());
        user.setRole(role);

        repository.save(user);

        Credential credential = new Credential();
        credential.setUser(user);
        credential.setUsername(request.getUsername());
        credential.setPassword(
                passwordEncoder.encode("iglesia2025")
        );
        credential.setStatus(StatusType.ACTIVE);

        credentialRepository.save(credential);

        user.setCredential(credential);

        return UserSystemMapper.toResponse(user);
    }

    // =========================================================
    // UPDATE
    // =========================================================

    @Override
    public UserSystemResponse update(
            UUID id,
            UserSystemUpdateRequest request
    ) {

        User user = getUser(id);

        boolean exists =
                repository.existsByDniAndOrganizationIsNullAndIdNot(
                        request.getDni(),
                        id
                );

        if (exists) {
            throw new Exceptions(
                    "System user with this DNI already exists",
                    HttpStatus.CONFLICT
            );
        }

        Role role = roleRepository.findById(request.getRolId())
                .orElseThrow(() ->
                        new Exceptions("Role not found", HttpStatus.NOT_FOUND)
                );

        user.setName(request.getName());
        user.setLastname(request.getLastname());
        user.setDni(request.getDni());
        user.setSex(request.getSex());
        user.setPhone(request.getPhone());
        user.setAddress(request.getAddress());
        user.setDateBirth(request.getDateBirth());
        user.setMaritalStatus(request.getMaritalStatus());
        user.setChildren(request.getChildren());
        user.setDateAdmission(request.getDateAdmission());
        user.setRole(role);

        repository.save(user);

        return UserSystemMapper.toResponse(user);
    }

    //********************** GLOBAL USER **********************//
    // =========================================================
    // ENABLE
    // =========================================================

    @Override
    public void enable(UUID userId) {

        User target = getUser(userId);

        authorizationService.assertCanAccessUser(
                authContext.getPrincipal(),
                toCredentialDetails(target)
        );

        target.getCredential().setStatus(StatusType.ACTIVE);

        credentialRepository.save(target.getCredential());
    }

    // =========================================================
    // DISABLE
    // =========================================================

    @Override
    public void disable(UUID userId) {

        User target = getUser(userId);

        authorizationService.assertCanAccessUser(
                authContext.getPrincipal(),
                toCredentialDetails(target)
        );

        target.getCredential().setStatus(StatusType.INACTIVE);

        credentialRepository.save(target.getCredential());
    }

    // =========================================================
    // DELETE
    // =========================================================

    @Override
    public void delete(UUID id) {

        User target = getUser(id);

        authorizationService.assertCanAccessUser(
                authContext.getPrincipal(),
                toCredentialDetails(target)
        );

        credentialRepository.delete(target.getCredential());
        repository.delete(target);
    }

    // =========================================================
    // CHANGE PASSWORD
    // =========================================================

    @Override
    public void changePassword(
            UUID id,
            UserChangePasswordRequest request
    ) {

        User target = getUser(id);

        authorizationService.assertCanAccessUser(
                authContext.getPrincipal(),
                toCredentialDetails(target)
        );

        target.getCredential().setPassword(
                passwordEncoder.encode(request.getPassword())
        );

        credentialRepository.save(target.getCredential());
    }

    // =========================================================
    // GET USER
    // =========================================================

    private User getUser(UUID id) {

        return repository.findById(id)
                .orElseThrow(() ->
                        new Exceptions(
                                "User not found",
                                HttpStatus.NOT_FOUND
                        )
                );
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
}