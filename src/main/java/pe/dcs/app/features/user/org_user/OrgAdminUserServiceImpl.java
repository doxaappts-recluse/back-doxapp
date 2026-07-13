package pe.dcs.app.features.user.org_user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.*;
import pe.dcs.app.features.user.org_user.mapper.OrgAdminMapper;
import pe.dcs.app.repository.*;
import pe.dcs.app.features.user.org_user.request.OrgAdminCreateRequest;
import pe.dcs.app.features.user.org_user.request.OrgAdminUpdateRequest;
import pe.dcs.app.features.user.org_user.response.OrgAdminResponse;
import pe.dcs.app.features.user.org_user.service.OrgAdminUserService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class OrgAdminUserServiceImpl implements OrgAdminUserService {

    /*private final PersonRepository userRepository;
    private final CredentialRepository credentialRepository;
    private final UserAccessRepository userAccessRepository;

    private final OrganizationRepository organizationRepository;
    private final BranchRepository branchRepository;
    private final RoleRepository roleRepository;

    private final PasswordEncoder passwordEncoder;

    @Override
    public OrgAdminResponse createOrgAdmin(OrgAdminCreateRequest request) {

        Organization organization = getOrganization(request.getOrganizationId());
        Branch branch = getBranch(request.getBranchId());

        validateOrganizationActive(organization);
        validateBranchBelongsOrganization(branch, organization);
        validateBranchActive(branch);
        validateOrgAdminNotExists(organization.getId());
        validateDniCreate(request.getDni());
        validateUsernameCreate(request.getUsername());

        Role role = getOrgAdminRole();

        // ==========================
        // USER
        // ==========================

        Person user = new Person();

        user.setBranch(branch);

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

        userRepository.save(user);

        // ==========================
        // CREDENTIAL
        // ==========================

        Credential credential = new Credential();

        credential.setPerson(user);
        credential.setUsername(request.getUsername());

        String password = request.getPassword();

        if (password == null || password.isBlank()) {
            password = "iglesia2025";
        }

        credential.setPassword(passwordEncoder.encode(password));
        credential.setStatus(StatusType.ACTIVE);

        credentialRepository.save(credential);

        user.setCredential(credential);

        // ==========================
        // ACCESS
        // ==========================

        UserAccess access = new UserAccess();

        access.setPerson(user);
        access.setOrganization(organization);
        access.setBranch(branch);
        access.setRole(role);
        access.setActive(true);

        userAccessRepository.save(access);

        return OrgAdminMapper.toResponse(user);
    }

    @Override
    public OrgAdminResponse updateOrgAdmin(UUID id, OrgAdminUpdateRequest request) {

        Person user = getUser(id);

        Branch branch = getBranch(request.getBranchId());

        UserAccess access = getActiveAccess(user);

        validateBranchBelongsOrganization(branch, access.getOrganization());
        validateBranchActive(branch);
        validateDniUpdate(request.getDni(), user.getId());
        validateUsernameUpdate(request.getUsername(), user.getCredential().getId());

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

        user.getCredential()
                .setUsername(
                        request.getUsername()
                );

        access.setBranch(branch);

        userRepository.save(user);

        return OrgAdminMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public OrgAdminResponse getOrgAdmin(UUID organizationId){

        UserAccess access =
                userAccessRepository
                        .findByOrganizationIdAndRoleValue(
                                organizationId,
                                RoleType.ORG_ADMIN
                        )
                        .orElseThrow(() ->
                                new Exceptions(
                                        "No existe administrador para la organización.",
                                        HttpStatus.NOT_FOUND
                                ));

        return OrgAdminMapper.toResponse(access.setPerson(););
    }

    private Person getUser(UUID id){
        return userRepository.findById(id)
                .orElseThrow(() ->
                        new Exceptions(
                                "Usuario no encontrado.",
                                HttpStatus.NOT_FOUND
                        ));
    }

    private Organization getOrganization(UUID id){
        return organizationRepository.findById(id)
                .orElseThrow(() ->
                        new Exceptions(
                                "Organización no encontrada.",
                                HttpStatus.NOT_FOUND
                        ));
    }

    private Branch getBranch(UUID id){
        return branchRepository.findById(id)
                .orElseThrow(() ->
                        new Exceptions(
                                "Sede no encontrada.",
                                HttpStatus.NOT_FOUND
                        ));
    }

    private Role getOrgAdminRole(){
        return roleRepository.findByValue(RoleType.ORG_ADMIN)
                .orElseThrow(() ->
                        new Exceptions(
                                "Rol ORG_ADMIN no encontrado.",
                                HttpStatus.NOT_FOUND
                        ));
    }

    private UserAccess getActiveAccess(Person user){

        return user.getAccesses()
                .stream()
                .filter(UserAccess::getActive)
                .findFirst()
                .orElseThrow(() ->
                        new Exceptions(
                                "El usuario no posee un acceso activo.",
                                HttpStatus.NOT_FOUND
                        ));
    }

    //VALIDACIONES

    private void validateOrganizationActive(Organization organization) {
        if (organization.getStatus() == StatusType.INACTIVE) {
            new Exceptions(
                    "La organización se encuentra deshabilitada.",
                    HttpStatus.CONFLICT
            );
        }
    }

    private void validateBranchActive(Branch branch) {
        if (branch.getStatus() == StatusType.INACTIVE) {
            new Exceptions(
                    "La sede se encuentra deshabilitada.",
                    HttpStatus.CONFLICT
            );
        }
    }

    private void validateBranchBelongsOrganization(Branch branch, Organization organization) {
        if (!branch.getOrganization().getId().equals(organization.getId())) {
            new Exceptions(
                    "La sede no pertenece a la organización seleccionada.",
                    HttpStatus.CONFLICT
            );
        }
    }

    private void validateOrgAdminNotExists(UUID organizationId) {

        boolean exists =
                userAccessRepository
                        .existsByOrganizationIdAndRoleValue(
                                organizationId,
                                "ORG_ADMIN"
                        );

        if (exists) {
            new Exceptions(
                    "La organización ya posee un administrador.",
                    HttpStatus.CONFLICT
            );
        }
    }

    private void validateDniCreate(String dni) {
        if (userRepository.existsByDni(dni)) {
            new Exceptions(
                    "El DNI ya se encuentra registrado.",
                    HttpStatus.CONFLICT
            );
        }
    }

    private void validateDniUpdate(String dni, UUID userId) {
        if (userRepository.existsByDniAndIdNot(dni, userId)) {
            new Exceptions(
                    "El DNI ya se encuentra registrado.",
                    HttpStatus.CONFLICT
            );
        }
    }

    private void validateUsernameCreate(String username) {
        if (credentialRepository.existsByUsername(username)) {
            new Exceptions(
                    "El nombre de usuario ya existe.",
                    HttpStatus.CONFLICT
            );
        }
    }

    private void validateUsernameUpdate(String username, UUID credentialId) {
        if (credentialRepository.existsByUsernameAndIdNot(username, credentialId)) {
            new Exceptions(
                    "El nombre de usuario ya existe.",
                    HttpStatus.CONFLICT
            );
        }
    }

    private void validateBranchHasOrganization(Branch branch) {
        if (branch.getOrganization() == null) {
            new Exceptions(
                    "La sede no pertenece a ninguna organización.",
                    HttpStatus.CONFLICT
            );
        }
    }

    private void validateBranchEnabled(Branch branch) {
        if (branch.getStatus() != StatusType.ACTIVE) {
            new Exceptions(
                    "Solo es posible asignar sedes activas.",
                    HttpStatus.CONFLICT
            );
        }
    }

    private void assignBranch(Person user, UserAccess access, Branch branch){
        user.setBranch(branch);
        access.setBranch(branch);
    }*/


}