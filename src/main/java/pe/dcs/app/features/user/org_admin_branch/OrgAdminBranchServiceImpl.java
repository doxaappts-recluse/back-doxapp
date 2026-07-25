package pe.dcs.app.features.user.org_admin_branch;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.*;
import pe.dcs.app.features.user.org_admin_branch.mapper.OrgAdminBranchMapper;
import pe.dcs.app.features.user.org_admin_branch.request.OrgAdminBranchCreateRequest;
import pe.dcs.app.features.user.org_admin_branch.request.OrgAdminBranchListRequest;
import pe.dcs.app.features.user.org_admin_branch.request.OrgAdminBranchUpdateRequest;
import pe.dcs.app.features.user.org_admin_branch.response.OrgAdminBranchDetailResponse;
import pe.dcs.app.features.user.org_admin_branch.response.OrgAdminBranchResponse;
import pe.dcs.app.features.user.org_admin_branch.service.OrgAdminBranchService;
import pe.dcs.app.repository.*;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.UserAccessHelper;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.resolveSort.OrgAdminBranchSort;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrgAdminBranchServiceImpl implements OrgAdminBranchService {

    private final PersonRepository personRepository;
    private final CredentialRepository credentialRepository;
    private final OrganizationRepository organizationRepository;
    private final RoleRepository roleRepository;
    private final BranchRepository branchRepository;
    private final PersonBranchRepository personBranchRepository;
    private final UserAccessRepository userAccessRepository;

    private final PasswordEncoder passwordEncoder;
    private final OrgAdminBranchMapper mapper;
    private final AuthContext authContext;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrgAdminBranchResponse> search(
            OrgAdminBranchListRequest request
    ) {

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts(),
                        OrgAdminBranchSort::resolvePath
                );

        Page<Person> page =
                personRepository.findAll(
                        OrgAdminBranchSpecification.filter(
                                request,
                                authContext
                        ),
                        pageable
                );

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(
                                mapper::toResponse
                        )
                        .toList(),

                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    @Override
    @Transactional
    public void create(
            OrgAdminBranchCreateRequest request
    ) {

        validateOrganizationAccess(request.getOrganizationId());
        validateDni(request.getDni());
        validateUsername(request.getUsername());

        /*
         * =============================
         * ORGANIZATION
         * =============================
         */
        Organization organization =
                organizationRepository.findById(request.getOrganizationId())
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Organización no encontrada",
                                        HttpStatus.NOT_FOUND
                                ));

        /*
         * =============================
         * ROLE
         * =============================
         */
        Role role =
                roleRepository.findById(request.getRoleId())
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Rol no encontrado",
                                        HttpStatus.NOT_FOUND
                                ));

        validateRole(role);

        /*
         * =============================
         * BRANCH
         * =============================
         */
        Branch branch = null;

        if (request.getBranchId() != null) {

            branch =
                    branchRepository.findById(request.getBranchId())
                            .orElseThrow(() ->
                                    new Exceptions(
                                            "Sede no encontrada",
                                            HttpStatus.NOT_FOUND
                                    ));

            validateBranchOrganization(
                    branch,
                    organization
            );
        }

        validateBranchRequired(
                role,
                branch
        );

        /*
         * =============================
         * PERSON
         * =============================
         */
        Person person = new Person();

        person.setName(request.getName());
        person.setLastname(request.getLastname());
        person.setSex(request.getSex());
        person.setPhone(request.getPhone());
        person.setDni(request.getDni());
        person.setAddress(request.getAddress());
        person.setDateBirth(request.getDateBirth());
        person.setMaritalStatus(request.getMaritalStatus());
        person.setChildren(request.getChildren());

        personRepository.save(person);

        /*
         * =============================
         * CREDENTIAL
         * =============================
         */
        Credential credential = new Credential();

        credential.setPerson(person);
        credential.setUsername(request.getUsername());
        credential.setPassword(
                passwordEncoder.encode(request.getPassword())
        );
        credential.setStatus(StatusType.ACTIVE);

        credentialRepository.save(credential);

        /*
         * =============================
         * USER ACCESS
         * =============================
         */
        UserAccess access = new UserAccess();

        access.setPerson(person);
        access.setOrganization(organization);
        access.setBranch(branch);
        access.setRole(role);
        access.setActive(StatusType.ACTIVE);

        userAccessRepository.save(access);

        /*
         * =============================
         * PERSON BRANCH
         * =============================
         */
        if (branch != null) {

            PersonBranch personBranch = new PersonBranch();

            personBranch.setPerson(person);
            personBranch.setBranch(branch);
            personBranch.setStatus(StatusType.ACTIVE);
            personBranch.setStartDate(request.getStartDate());
            personBranch.setEndDate(null);
            personBranch.setTransferReason(null);

            personBranchRepository.save(personBranch);

            /*
             * Mantener sincronizada la relación
             */
            person.getBranchHistory().add(personBranch);
        }

        /*
         * =============================
         * RELACIÓN PERSON -> CREDENTIAL
         * =============================
         */
        person.setCredential(credential);

        /*
         * =============================
         * RELACIÓN PERSON -> ACCESS
         * =============================
         */
        person.getAccesses().add(access);
    }

    @Override
    @Transactional(readOnly = true)
    public OrgAdminBranchDetailResponse findById(UUID id) {

        Person person = personRepository.findById(id)
                .orElseThrow(() ->
                        new Exceptions("Usuario no encontrado", HttpStatus.NOT_FOUND));

        validatePersonAccess(person);

        return mapper.toDetailResponse(person);
    }

    private void validateOrganizationAccess(UUID organizationId){

        if(authContext.isSystem()){
            return;
        }

        if(!authContext.hasOrganizationAccess(organizationId)){

            throw new Exceptions(
                    "No tiene acceso a esta organización", HttpStatus.UNAUTHORIZED
            );

        }
    }

    @Override
    @Transactional
    public void update(
            UUID id,
            OrgAdminBranchUpdateRequest request
    ) {

        Person person =
                personRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Usuario no encontrado",
                                        HttpStatus.NOT_FOUND
                                ));

        validatePersonAccess(person);

        validateDniForUpdate(
                person.getId(),
                request.getDni()
        );

        validateUsernameForUpdate(
                person.getCredential().getId(),
                request.getUsername()
        );

        person.setName(request.getName());
        person.setLastname(request.getLastname());
        person.setSex(request.getSex());
        person.setPhone(request.getPhone());
        person.setDni(request.getDni());
        person.setAddress(request.getAddress());
        person.setDateBirth(request.getDateBirth());
        person.setChildren(request.getChildren());
        person.setMaritalStatus(request.getMaritalStatus());

        Credential credential = person.getCredential();

        credential.setUsername(request.getUsername());

        personRepository.save(person);
        credentialRepository.save(credential);

    }

    @Override
    @Transactional
    public void enable(UUID id) {

        Person person = personRepository.findById(id)
                .orElseThrow(() ->
                        new Exceptions("Usuario no encontrado", HttpStatus.NOT_FOUND)
                );

        validatePersonAccess(person);

        Credential credential = person.getCredential();

        if (credential == null) {
            throw new Exceptions("El usuario no tiene credencial.", HttpStatus.CONFLICT);
        }

        credential.setStatus(StatusType.ACTIVE);

        credentialRepository.save(credential);
    }

    @Override
    @Transactional
    public void disable(
            UUID id
    ) {

        Person person = personRepository.findById(id)
                .orElseThrow(() ->
                        new Exceptions("Usuario no encontrado", HttpStatus.NOT_FOUND)
                );

        validatePersonAccess(person);

        Credential credential = person.getCredential();

        if (credential == null) {
            throw new Exceptions("El usuario no tiene credencial.", HttpStatus.CONFLICT);
        }

        credential.setStatus(StatusType.INACTIVE);

        credentialRepository.save(credential);

    }

    private void validatePersonAccess(Person person) {

        UserAccess access =
                UserAccessHelper.getActiveAccess(
                        person
                );

        if (access == null) {
            throw new Exceptions("El usuario no tiene un acceso activo.", HttpStatus.CONFLICT);
        }

        UUID organizationId = access.getOrganization().getId();

        if (!authContext.canAccess(
                organizationId,
                access.getBranch() != null
                        ? access.getBranch().getId()
                        : null
        )) {

            throw new Exceptions("No tiene permisos para administrar este usuario.", HttpStatus.UNAUTHORIZED);

        }

    }

    private void validateDniForUpdate(UUID personId, String dni) {
        if (personRepository.existsByDniAndIdNot(dni, personId)) {
            throw new Exceptions("El DNI ya existe", HttpStatus.CONFLICT);
        }
    }

    private void validateUsernameForUpdate(UUID credentialId, String username) {
        if (credentialRepository.existsByUsernameAndIdNot(username, credentialId)) {
            throw new Exceptions("El username ya existe", HttpStatus.CONFLICT);
        }
    }

    private void validateDni(String dni){
        if(personRepository.existsByDni(dni)){
            throw new Exceptions(
                    "El DNI ya está registrado", HttpStatus.CONFLICT
            );
        }
    }

    private void validateUsername(String username){
        if(credentialRepository.existsByUsername(username)){
            throw new Exceptions(
                    "El username ya existe", HttpStatus.CONFLICT
            );
        }
    }

    private void validateRole(Role role){
        if(role.getValue() != RoleType.ORG_ADMIN && role.getValue() != RoleType.ORG_BRANCH_ADMIN){
            throw new Exceptions(
                    "Rol no permitido", HttpStatus.CONFLICT
            );
        }
    }

    private void validateBranchOrganization(Branch branch, Organization organization){
        if(!branch.getOrganization().getId().equals(organization.getId())){
            throw new Exceptions(
                    "La sede no pertenece a la organización", HttpStatus.BAD_REQUEST
            );
        }
    }

    private void validateBranchRequired(Role role, Branch branch){
        if(role.getValue() == RoleType.ORG_BRANCH_ADMIN && branch == null){
            throw new Exceptions(
                    "El rol administrador de la organización requiere una sede", HttpStatus.BAD_REQUEST
            );
        }
    }
}