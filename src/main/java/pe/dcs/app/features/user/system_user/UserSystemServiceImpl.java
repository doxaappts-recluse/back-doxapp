package pe.dcs.app.features.user.system_user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Credential;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.Role;
import pe.dcs.app.entity.UserAccess;
import pe.dcs.app.features.user.shared.UserChangePasswordRequest;
import pe.dcs.app.features.user.system_user.mapper.UserSystemMapper;
import pe.dcs.app.features.user.system_user.request.UserSystemCreateRequest;
import pe.dcs.app.features.user.system_user.request.UserSystemListRequest;
import pe.dcs.app.features.user.system_user.request.UserSystemUpdateRequest;
import pe.dcs.app.features.user.system_user.response.UserSystemResponse;
import pe.dcs.app.features.user.system_user.service.UserSystemService;
import pe.dcs.app.repository.CredentialRepository;
import pe.dcs.app.repository.RoleRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.security.service.UserAccessContext;
import pe.dcs.app.security.service.credentials.CredentialDetailsImpl;
import pe.dcs.app.service.AuthorizationService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.resolveSort.PersonSort;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;
import pe.dcs.app.repository.PersonRepository;
import pe.dcs.app.repository.UserAccessRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserSystemServiceImpl implements UserSystemService {

    private final PersonRepository repository;
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
    @Transactional(readOnly = true)
    public PageResponse<UserSystemResponse> findAllSystem(
            UserSystemListRequest request
    ) {

        Pageable pageable = PageableUtil.buildPageable(
                request.getPagination(),
                request.getSorts(),
                PersonSort::resolvePath
        );

        Page<Person> page = repository.findAll(
                UserSystemSpecification.filter(
                        request
                ),
                pageable
        );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(person -> UserSystemMapper.toResponse(person, showAudit))
                        .toList(),
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    /*@Override
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
    }*/

    // =========================================================
    // FIND BY ID
    // =========================================================

    @Override
    public UserSystemResponse findById(UUID id) {
        return UserSystemMapper.toResponse(getUser(id), authContext.canViewAudit());
    }

    // =========================================================
    // CREATE
    // =========================================================

    @Override
    @Transactional
    public UserSystemResponse create(UserSystemCreateRequest request) {

        if (repository.existsByDni(request.getDni())) {
            throw new Exceptions(
                    "error.userWithDniAlreadyExists",
                    HttpStatus.CONFLICT
            );
        }

        if (credentialRepository.existsByUsername(request.getUsername())) {
            throw new Exceptions(
                    "error.usernameAlreadyExists",
                    HttpStatus.CONFLICT
            );
        }

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() ->
                        new Exceptions(
                                "error.roleNotFound",
                                HttpStatus.NOT_FOUND
                        )
                );

        if (!role.isSystemRole()) {
            throw new Exceptions(
                    "error.selectedRoleNotSystemRole",
                    HttpStatus.BAD_REQUEST
            );
        }

        // =====================================
        // PERSON
        // =====================================

        Person person = new Person();

        person.setName(request.getName());
        person.setLastname(request.getLastname());
        person.setDni(request.getDni());
        person.setSex(request.getSex());
        person.setPhone(request.getPhone());
        person.setAddress(request.getAddress());
        person.setDateBirth(request.getDateBirth());
        person.setMaritalStatus(request.getMaritalStatus());
        person.setChildren(request.getChildren());
        person.setDateAdmission(request.getDateAdmission());

        repository.save(person);

        // =====================================
        // CREDENTIAL
        // =====================================

        Credential credential = new Credential();

        credential.setPerson(person);
        credential.setUsername(request.getUsername());
        credential.setPassword(
                passwordEncoder.encode("iglesia2025")
        );
        credential.setStatus(StatusType.ACTIVE);

        credentialRepository.save(credential);

        person.setCredential(credential);

        // =====================================
        // ACCESS
        // =====================================

        UserAccess access = new UserAccess();

        access.setPerson(person);
        access.setRole(role);

        access.setOrganization(null);
        access.setBranch(null);

        access.setActive(StatusType.ACTIVE);

        userAccessRepository.save(access);

        return UserSystemMapper.toResponse(person, authContext.canViewAudit());
    }
    // =========================================================
    // UPDATE
    // =========================================================

    @Override
    @Transactional
    public UserSystemResponse update(UUID id, UserSystemUpdateRequest request) {

        Person person = getUser(id);

        if (repository.existsByDniAndIdNot(request.getDni(), id)) {
            throw new Exceptions(
                    "error.userWithDniAlreadyExists",
                    HttpStatus.CONFLICT
            );
        }

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() ->
                        new Exceptions(
                                "error.roleNotFound",
                                HttpStatus.NOT_FOUND
                        )
                );

        if (!role.isSystemRole()) {
            throw new Exceptions(
                    "error.selectedRoleNotSystemRole",
                    HttpStatus.BAD_REQUEST
            );
        }

        person.setName(request.getName());
        person.setLastname(request.getLastname());
        person.setDni(request.getDni());
        person.setSex(request.getSex());
        person.setPhone(request.getPhone());
        person.setAddress(request.getAddress());
        person.setDateBirth(request.getDateBirth());
        person.setMaritalStatus(request.getMaritalStatus());
        person.setChildren(request.getChildren());
        person.setDateAdmission(request.getDateAdmission());

        UserAccess access = person.getAccesses()
                .stream()
                .filter(a -> a.getActive() == StatusType.ACTIVE)
                .findFirst()
                .orElseThrow(() ->
                        new Exceptions(
                                "error.activeAccessNotFound",
                                HttpStatus.NOT_FOUND
                        )
                );

        access.setRole(role);

        repository.save(person);

        return UserSystemMapper.toResponse(person, authContext.canViewAudit());
    }

    // =========================================================
    // ENABLE
    // =========================================================
    @Override
    @Transactional
    public void enable(UUID id) {

        Person person = getUser(id);

        authorizationService.assertCanAccessUser(
                authContext.getPrincipal(),
                toCredentialDetails(person)
        );

        person.getCredential().setStatus(StatusType.ACTIVE);

        credentialRepository.save(person.getCredential());
    }

    // =========================================================
    // DISABLE
    // =========================================================

    @Override
    @Transactional
    public void disable(UUID id) {

        Person person = getUser(id);

        authorizationService.assertCanAccessUser(
                authContext.getPrincipal(),
                toCredentialDetails(person)
        );

        person.getCredential().setStatus(StatusType.INACTIVE);

        credentialRepository.save(person.getCredential());
    }

    // =========================================================
    // DELETE
    // =========================================================

    @Override
    @Transactional
    public void delete(UUID id) {

        Person person = getUser(id);

        authorizationService.assertCanAccessUser(
                authContext.getPrincipal(),
                toCredentialDetails(person)
        );

        credentialRepository.delete(person.getCredential());

        repository.delete(person);
    }

    // =========================================================
    // CHANGE PASSWORD
    // =========================================================

    @Override
    @Transactional
    public void changePassword(
            UUID id,
            UserChangePasswordRequest request
    ) {

        Person person = getUser(id);

        authorizationService.assertCanAccessUser(
                authContext.getPrincipal(),
                toCredentialDetails(person)
        );

        person.getCredential().setPassword(
                passwordEncoder.encode(request.getPassword())
        );

        credentialRepository.save(person.getCredential());
    }

    // =========================================================
    // GET USER
    // =========================================================

    private Person getUser(UUID id) {

        return repository.findById(id)
                .orElseThrow(() ->
                        new Exceptions(
                                "error.userNotFound",
                                HttpStatus.NOT_FOUND
                        )
                );
    }

    // =========================================================
    // TARGET USER -> SECURITY MODEL
    // =========================================================

    private CredentialDetailsImpl toCredentialDetails(Person user) {

        Credential credential = user.getCredential();

        List<UserAccessContext> accesses =
                user.getAccesses()
                        .stream()
                        .map(access ->
                                new UserAccessContext(
                                        access.getOrganization() != null
                                                ? access.getOrganization().getId()
                                                : null,
                                        access.getBranch() != null
                                                ? access.getBranch().getId()
                                                : null,
                                        access.getRole().getValue()
                                        )
                        )
                        .toList();


        Collection<GrantedAuthority> authorities =
                accesses
                        .stream()
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
                credential.getStatus() == StatusType.ACTIVE,
                accesses,
                authorities,
                null,
                null
        );
    }
}