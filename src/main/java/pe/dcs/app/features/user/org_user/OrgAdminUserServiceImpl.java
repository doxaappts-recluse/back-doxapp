package pe.dcs.app.features.user.org_user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.*;
import pe.dcs.app.repository.*;
import pe.dcs.app.features.user.org_user.request.OrgAdminCreateRequest;
import pe.dcs.app.features.user.org_user.request.OrgAdminUpdateRequest;
import pe.dcs.app.features.user.org_user.response.OrgAdminResponse;
import pe.dcs.app.features.user.org_user.service.OrgAdminUserService;
import pe.dcs.app.features.user.system_user.mapper.UserSystemMapper;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrgAdminUserServiceImpl implements OrgAdminUserService {

    /*private final UserRepository userRepository;
    private final CredentialRepository credentialRepository;
    private final OrganizationRepository organizationRepository;
    private final UserAccessRepository userAccessRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    // =========================================================
    // CREATE ORG ADMIN
    // =========================================================

    @Override
    @Transactional
    public OrgAdminResponse createOrgAdmin(
            OrgAdminCreateRequest request
    ) {

        Organization org =
                organizationRepository
                        .findById(request.getOrganizationId())
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Organization not found",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        Role role =
                roleRepository
                        .findByValue("ORG_ADMIN")
                        .orElseThrow(() ->
                                new Exceptions(
                                        "ORG_ADMIN role not found",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        boolean existsAdmin =
                userAccessRepository
                        .existsByOrganizationIdAndRoleValue(
                                org.getId(),
                                "ORG_ADMIN"
                        );

        if (existsAdmin) {

            throw new Exceptions(
                    "Organization already has an ORG_ADMIN",
                    HttpStatus.CONFLICT
            );
        }

        boolean existsDni =
                userRepository
                        .existsByDni(
                                request.getDni()
                        );

        if (existsDni) {

            throw new Exceptions(
                    "DNI already exists",
                    HttpStatus.CONFLICT
            );
        }

        boolean existsUsername =
                credentialRepository
                        .existsByUsername(
                                request.getUsername()
                        );

        if (existsUsername) {
            throw new Exceptions(
                    "Username already exists",
                    HttpStatus.CONFLICT
            );
        }

        // =========================
        // USER
        // =========================

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

        user = userRepository.save(user);

        // =========================
        // CREDENTIAL
        // =========================

        Credential credential = new Credential();

        credential.setUser(user);
        credential.setUsername(
                request.getUsername()
        );

        String rawPassword =
                request.getPassword();

        if(rawPassword == null || rawPassword.isBlank()){
            rawPassword = "iglesia2026";
        }

        credential.setPassword(
                passwordEncoder.encode(rawPassword)
        );

        credential.setStatus(
                StatusType.ACTIVE
        );

        credentialRepository.save(credential);

        user.setCredential(credential);

        // =========================
        // USER ACCESS
        // =========================

        UserAccess access = new UserAccess();

        access.setUser(user);
        access.setOrganization(org);
        // null = toda la organización
        access.setBranch(null);
        access.setRole(role);
        access.setActive(true);

        userAccessRepository.save(access);

        return UserSystemMapper
                .mapToOrgAdminResponse(user);
    }

    @Override
    @Transactional
    public OrgAdminResponse updateOrgAdmin(
            UUID id,
            OrgAdminUpdateRequest request
    ) {

        User user = getUser(id);

        if (request.getName() != null) {
            user.setName(request.getName());
        }

        if (request.getLastname() != null) {
            user.setLastname(request.getLastname());
        }

        if (request.getDni() != null) {
            user.setDni(request.getDni());
        }

        if (request.getSex() != null) {
            user.setSex(request.getSex());
        }

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }

        if (request.getAddress() != null) {
            user.setAddress(request.getAddress());
        }

        if (request.getDateBirth() != null) {
            user.setDateBirth(request.getDateBirth());
        }

        if (request.getMaritalStatus() != null) {
            user.setMaritalStatus(request.getMaritalStatus());
        }

        if (request.getChildren() != null) {
            user.setChildren(request.getChildren());
        }

        if (request.getDateAdmission() != null) {
            user.setDateAdmission(request.getDateAdmission());
        }

        Credential credential = user.getCredential();

        if (request.getUsername() != null) {

            boolean existsUsername =
                    credentialRepository.existsByUsernameAndIdNot(
                            request.getUsername(),
                            credential.getId()
                    );

            if (existsUsername) {
                throw new Exceptions(
                        "Username already exists",
                        HttpStatus.CONFLICT
                );
            }

            credential.setUsername(request.getUsername());
        }

        userRepository.save(user);

        return UserSystemMapper.mapToOrgAdminResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public OrgAdminResponse getOrgAdmin(UUID orgId) {

        Credential credential = credentialRepository
                .findByUser_Organization_IdAndRole_Value(
                        orgId,
                        "ORG_ADMIN"
                )
                .orElseThrow(() ->
                        new Exceptions(
                                "ORG_ADMIN not found",
                                HttpStatus.NOT_FOUND
                        )
                );

        return UserSystemMapper.mapToOrgAdminResponse(
                credential.getUser()
        );
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
    }*/

}