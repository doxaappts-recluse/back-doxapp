package pe.dcs.app.features.user.org_admin_branch;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.*;
import pe.dcs.app.features.contract.LicenseGuard;
import pe.dcs.app.features.user.org_admin_branch.mapper.OrgAdminBranchMapper;
import pe.dcs.app.features.user.org_admin_branch.request.OrgAdminBranchAddAccessRequest;
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
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.resolveSort.OrgAdminBranchSort;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.util.List;
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
    private final LicenseGuard licenseGuard;

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

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(
                                person -> mapper.toResponse(person, showAudit)
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
                                        "error.organizacionNoEncontrada",
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
                                        "error.rolNoEncontrado",
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
                                            "error.sedeNoEncontrada",
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

        licenseGuard.assertLicenseAvailable(organization, branch);

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
                        new Exceptions("error.usuarioNoEncontrado", HttpStatus.NOT_FOUND));

        validatePersonAccess(person);

        return mapper.toDetailResponse(person);
    }

    private void validateOrganizationAccess(UUID organizationId){

        if(authContext.isSystem()){
            return;
        }

        if(!authContext.hasOrganizationAccess(organizationId)){

            throw new Exceptions(
                    "error.noTieneAccesoOrganizacion", HttpStatus.UNAUTHORIZED
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
                                        "error.usuarioNoEncontrado",
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
                        new Exceptions("error.usuarioNoEncontrado", HttpStatus.NOT_FOUND)
                );

        validatePersonAccess(person);

        Credential credential = person.getCredential();

        if (credential == null) {
            throw new Exceptions("error.usuarioNoTieneCredencial", HttpStatus.CONFLICT);
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
                        new Exceptions("error.usuarioNoEncontrado", HttpStatus.NOT_FOUND)
                );

        validatePersonAccess(person);

        Credential credential = person.getCredential();

        if (credential == null) {
            throw new Exceptions("error.usuarioNoTieneCredencial", HttpStatus.CONFLICT);
        }

        credential.setStatus(StatusType.INACTIVE);

        credentialRepository.save(credential);

    }

    /**
     * Autoriza si el llamante puede administrar AL MENOS UNO de
     * los accesos de la persona (una persona puede tener varios,
     * uno por sede).
     *
     * Se evalúan TODOS los accesos (activos e inactivos), no solo
     * los activos: una persona con todos sus accesos deshabilitados
     * (p.ej. un ORG_ADMIN al que se le deshabilitó su único acceso
     * global) sigue debiendo poder verse/editarse, para poder
     * reactivar ese acceso o asignarle uno nuevo. Si se exigiera un
     * acceso ACTIVO acá, deshabilitar el último acceso dejaría a la
     * persona inaccesible ("no tiene un acceso activo") y sin forma
     * de revertirlo desde la UI.
     */
    private void validatePersonAccess(Person person) {

        List<UserAccess> accesses = person.getAccesses();

        if (accesses.isEmpty()) {
            throw new Exceptions("error.usuarioNoTieneNingunAccesoRegistrado", HttpStatus.CONFLICT);
        }

        boolean canManage =
                accesses.stream()
                        .anyMatch(access ->
                                authContext.canAccess(
                                        access.getOrganization() != null
                                                ? access.getOrganization().getId()
                                                : null,
                                        access.getBranch() != null
                                                ? access.getBranch().getId()
                                                : null
                                )
                        );

        if (!canManage) {
            throw new Exceptions("error.noTienePermisosAdministrarUsuario", HttpStatus.UNAUTHORIZED);
        }

    }

    private void validateDniForUpdate(UUID personId, String dni) {
        if (personRepository.existsByDniAndIdNot(dni, personId)) {
            throw new Exceptions("error.elDniYaExiste", HttpStatus.CONFLICT);
        }
    }

    private void validateUsernameForUpdate(UUID credentialId, String username) {
        if (credentialRepository.existsByUsernameAndIdNot(username, credentialId)) {
            throw new Exceptions("error.elUsernameYaExiste", HttpStatus.CONFLICT);
        }
    }

    private void validateDni(String dni){
        if(personRepository.existsByDni(dni)){
            throw new Exceptions(
                    "error.elDniYaEstaRegistrado", HttpStatus.CONFLICT
            );
        }
    }

    private void validateUsername(String username){
        if(credentialRepository.existsByUsername(username)){
            throw new Exceptions(
                    "error.elUsernameYaExiste", HttpStatus.CONFLICT
            );
        }
    }

    /**
     * Este módulo solo crea/gestiona accesos a nivel de
     * organización: ORG_ADMIN (global), ORG_BRANCH_ADMIN y
     * ORG_USER (ambos por sede). Los roles de SISTEMA se crean
     * desde el módulo de Usuarios del Sistema.
     */
    private void validateRole(Role role){
        if(role.isSystemRole()){
            throw new Exceptions(
                    "error.rolNoPermitido", HttpStatus.CONFLICT
            );
        }
    }

    private void validateBranchOrganization(Branch branch, Organization organization){
        if(!branch.getOrganization().getId().equals(organization.getId())){
            throw new Exceptions(
                    "error.sedeNoPerteneceOrganizacion", HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * ORG_BRANCH_ADMIN y ORG_USER aplican a una sede puntual;
     * solo ORG_ADMIN es global (sin sede). Se valida en ambos
     * sentidos: antes solo se rechazaba "rol de sede sin sede", pero
     * no el caso inverso (ORG_ADMIN CON sede) — eso permitía que un
     * front con un bug (branchId con un valor viejo seleccionado
     * antes de cambiar el rol a ORG_ADMIN) creara un UserAccess de
     * ORG_ADMIN con sede, que después ni matchea
     * hasOrganizationAdminAccess() (exige branchId nulo) ni
     * hasBranchAdminAccess() (exige rol ORG_BRANCH_ADMIN): ese
     * usuario terminaba en RoleType.UNKNOWN con el sidebar vacío al
     * loguear, sin ningún error visible al crearlo.
     */
    private void validateBranchRequired(Role role, Branch branch){

        if(role.isBranchRole() && branch == null){
            throw new Exceptions(
                    "error.rolRequiereSede", HttpStatus.BAD_REQUEST
            );
        }

        if(role.isOrganizationAdmin() && branch != null){
            throw new Exceptions(
                    "error.rolOrgAdminNoDebeTenerSede", HttpStatus.BAD_REQUEST
            );
        }
    }

    // =========================================================
    // ACCESOS ADICIONALES (múltiples sedes/roles por persona)
    // =========================================================

    @Override
    @Transactional
    public void addAccess(
            UUID personId,
            OrgAdminBranchAddAccessRequest request
    ) {

        Person person =
                personRepository.findById(personId)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.usuarioNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                ));

        validatePersonAccess(person);

        if (UserAccessHelper.hasActiveOrganizationAdminAccess(person)) {

            throw new Exceptions(
                    "error.usuarioAdministradorOrganizacionAccesoGlobalNo",
                    HttpStatus.CONFLICT
            );
        }

        Branch branch =
                branchRepository.findById(request.getBranchId())
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.sedeNoEncontrada",
                                        HttpStatus.NOT_FOUND
                                ));

        Role role =
                roleRepository.findById(request.getRoleId())
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.rolNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                ));

        if (!role.isBranchRole()) {

            throw new Exceptions(
                    "error.soloPuedenAgregarAccesosRolAdministrador",
                    HttpStatus.BAD_REQUEST
            );
        }

        Organization organization = branch.getOrganization();

        UUID personOrganizationId = resolvePersonOrganizationId(person);

        if (personOrganizationId != null
                && !personOrganizationId.equals(organization.getId())) {

            throw new Exceptions(
                    "error.sedeDebePertenecerMismaOrganizacionAccesos",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (!authContext.canAccess(organization.getId(), branch.getId())) {

            throw new Exceptions(
                    "error.noTienePermisosAdministrarAccesosSede",
                    HttpStatus.UNAUTHORIZED
            );
        }

        UserAccess existing =
                userAccessRepository.findByPersonIdAndOrganizationIdAndBranchIdAndRoleId(
                                person.getId(),
                                organization.getId(),
                                branch.getId(),
                                role.getId()
                        )
                        .orElse(null);

        if (existing != null) {

            if (existing.getActive() == StatusType.ACTIVE) {

                throw new Exceptions(
                        "error.usuarioTieneAcceso",
                        HttpStatus.CONFLICT
                );
            }

            licenseGuard.assertLicenseAvailable(organization, branch);

            existing.setActive(StatusType.ACTIVE);

            userAccessRepository.save(existing);

            return;
        }

        licenseGuard.assertLicenseAvailable(organization, branch);

        UserAccess access = new UserAccess();

        access.setPerson(person);
        access.setOrganization(organization);
        access.setBranch(branch);
        access.setRole(role);
        access.setActive(StatusType.ACTIVE);

        userAccessRepository.save(access);

        person.getAccesses().add(access);
    }

    /**
     * ORG_ADMIN es un acceso global (acceso completo a la
     * organización) y no debería convivir con otros accesos
     * activos de la misma persona (ORG_BRANCH_ADMIN/ORG_USER en
     * sedes puntuales) — si conviven, la resolución de contexto al
     * loguear (getAvailableContexts) no tiene forma de priorizar
     * un rol sobre otro. Se valida en ambas direcciones:
     * - No se puede activar un acceso puntual si ya hay un
     *   ORG_ADMIN activo (mismo chequeo que ya existía en
     *   addAccess()).
     * - No se puede activar el acceso ORG_ADMIN si la persona
     *   tiene otros accesos activos (deben desactivarse antes).
     */
    @Override
    @Transactional
    public void enableAccess(UUID accessId) {

        UserAccess access = findAccessOrThrow(accessId);

        validateAccessManagePermission(access);

        Person person = access.getPerson();

        if (access.isOrganizationAdmin()) {

            boolean hasOtherActiveAccess =
                    person.getAccesses()
                            .stream()
                            .anyMatch(a ->
                                    !a.getId().equals(access.getId())
                                            && a.getActive() == StatusType.ACTIVE
                            );

            if (hasOtherActiveAccess) {

                throw new Exceptions(
                        "error.debeDesactivarOtrosAccesosAntesDeActivarAdminOrganizacion",
                        HttpStatus.CONFLICT
                );
            }

        } else if (UserAccessHelper.hasActiveOrganizationAdminAccess(person)) {

            throw new Exceptions(
                    "error.usuarioAdministradorOrganizacionAccesoGlobalNo",
                    HttpStatus.CONFLICT
            );
        }

        licenseGuard.assertLicenseAvailable(
                access.getOrganization(),
                access.getBranch()
        );

        access.setActive(StatusType.ACTIVE);

        userAccessRepository.save(access);
    }

    @Override
    @Transactional
    public void disableAccess(UUID accessId) {

        UserAccess access = findAccessOrThrow(accessId);

        validateAccessManagePermission(access);

        access.setActive(StatusType.INACTIVE);

        userAccessRepository.save(access);
    }

    private UserAccess findAccessOrThrow(UUID accessId) {

        return userAccessRepository.findById(accessId)
                .orElseThrow(() ->
                        new Exceptions(
                                "error.accesoNoEncontrado",
                                HttpStatus.NOT_FOUND
                        ));
    }

    private void validateAccessManagePermission(UserAccess access) {

        if (!authContext.canAccess(
                access.getOrganization() != null
                        ? access.getOrganization().getId()
                        : null,
                access.getBranch() != null
                        ? access.getBranch().getId()
                        : null
        )) {

            throw new Exceptions(
                    "error.noTienePermisosAdministrarAcceso",
                    HttpStatus.UNAUTHORIZED
            );
        }
    }

    /**
     * Organización de los accesos ya existentes de la persona
     * (todos comparten la misma organización). Null si aún no
     * tiene ningún acceso.
     */
    private UUID resolvePersonOrganizationId(Person person) {

        return person.getAccesses()
                .stream()
                .filter(a -> a.getOrganization() != null)
                .map(a -> a.getOrganization().getId())
                .findFirst()
                .orElse(null);
    }
}