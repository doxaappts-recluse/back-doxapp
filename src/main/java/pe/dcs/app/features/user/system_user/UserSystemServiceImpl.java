package pe.dcs.app.features.user.system_user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.dcs.app.features.user.system_user.service.UserSystemService;

@Service
@RequiredArgsConstructor
public class UserSystemServiceImpl implements UserSystemService {

    /*private final UserRepository repository;
    private final CredentialRepository credentialRepository;
    private final RoleRepository roleRepository;
    private final UserAccessRepository userAccessRepository;
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
    @Transactional
    public UserSystemResponse create(UserSystemCreateRequest request) {

        boolean exists =  repository.existsByDni(request.getDni());

        if (exists) {
            throw new Exceptions(
                    "System user with this DNI already exists",
                    HttpStatus.CONFLICT
            );
        }

        if (credentialRepository.existsByUsername(request.getUsername())) {

            throw new Exceptions(
                    "Username already exists",
                    HttpStatus.CONFLICT
            );
        }

        Role role =
                roleRepository.findById(request.getRolId())
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Role not found",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (!role.getValue().startsWith("SYSTEM_")) {

            throw new Exceptions(
                    "Invalid system role",
                    HttpStatus.BAD_REQUEST
            );
        }

        // =============================
        // PERSON
        // =============================

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

        user = repository.save(user);

        // =============================
        // LOGIN
        // =============================

        Credential credential = new Credential();

        credential.setUser(user);
        credential.setUsername(request.getUsername());
        credential.setPassword(
                passwordEncoder.encode("iglesia2025")
        );
        credential.setStatus(
                StatusType.ACTIVE
        );

        credentialRepository.save(credential);

        user.setCredential(credential);

        // =============================
        // SYSTEM ACCESS
        // =============================

        UserAccess access = new UserAccess();

        access.setUser(user);
        access.setRole(role);

        // SYSTEM NO TIENE ORGANIZACION NI SEDE
        access.setOrganization(null);
        access.setBranch(null);

        access.setActive(true);

        userAccessRepository.save(access);

        return UserSystemMapper.toResponse(user);
    }

    // =========================================================
    // UPDATE
    // =========================================================

    @Override
    @Transactional
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
                        new Exceptions(
                                "Role not found",
                                HttpStatus.NOT_FOUND
                        )
                );

        if (!role.getValue().startsWith("SYSTEM_")) {
            throw new Exceptions(
                    "Invalid system role",
                    HttpStatus.BAD_REQUEST
            );
        }

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

        Credential credential = user.getCredential();

        if (credential == null) {
            throw new Exceptions(
                    "Credential not found",
                    HttpStatus.NOT_FOUND
            );
        }

        credential.setRole(role);

        repository.save(user);

        return UserSystemMapper.toResponse(user);
    }

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

        Credential credential = user.getCredential();

        return new CredentialDetailsImpl(
                credential.getId(),
                user.getId(),

                user.getOrganization() != null
                        ? user.getOrganization().getId()
                        : null,

                user.getBranch() != null
                        ? user.getBranch().getId()
                        : null,

                credential.getUsername(),
                credential.getPassword(),

                user.getName(),
                user.getLastname(),

                credential.isActive(),

                List.of(
                        new SimpleGrantedAuthority(
                                credential.getRole().getValue()
                        )
                )
        );
    }*/
}