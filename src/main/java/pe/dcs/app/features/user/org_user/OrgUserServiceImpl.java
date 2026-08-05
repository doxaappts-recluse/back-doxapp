package pe.dcs.app.features.user.org_user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.PersonBranch;
import pe.dcs.app.features.user.org_user.mapper.OrgUserMapper;
import pe.dcs.app.features.user.org_user.request.OrgUserCreateRequest;
import pe.dcs.app.features.user.org_user.request.OrgUserSearchRequest;
import pe.dcs.app.features.user.org_user.request.OrgUserUpdateRequest;
import pe.dcs.app.features.user.org_user.response.OrgUserResponse;
import pe.dcs.app.features.user.org_user.response.OrgUserSearchRowResponse;
import pe.dcs.app.features.user.org_user.service.OrgUserService;
import pe.dcs.app.repository.BranchRepository;
import pe.dcs.app.repository.PersonBranchRepository;
import pe.dcs.app.repository.PersonRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.resolveSort.PersonSort;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Crea/edita/lista personas dentro de la organización/sede del
 * contexto de quien hace la petición (ORG_ADMIN / ORG_BRANCH_ADMIN).
 * Acá solo se crea la Person + su PersonBranch (sede tomada del
 * contexto). No maneja credenciales ni acceso al sistema: eso es
 * responsabilidad de otro flujo.
 */
@Service
@RequiredArgsConstructor
public class OrgUserServiceImpl implements OrgUserService {

    private final PersonRepository personRepository;
    private final BranchRepository branchRepository;
    private final PersonBranchRepository personBranchRepository;

    private final OrgUserMapper mapper;
    private final AuthContext authContext;

    // =====================================================
    // SEARCH
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrgUserSearchRowResponse> search(OrgUserSearchRequest request) {

        assertCallerIsOrgOrBranchAdmin();

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts(),
                        PersonSort::resolvePath
                );

        Page<Person> page =
                personRepository.findAll(
                        OrgUserSpecification.filter(request, authContext),
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(person -> mapper.toSearchRow(person, showAudit))
                        .toList(),

                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    // =====================================================
    // CREATE
    // =====================================================

    @Override
    @Transactional
    public OrgUserResponse create(OrgUserCreateRequest request) {

        assertCallerIsOrgOrBranchAdmin();

        validateMaritalStatus(request.getMaritalStatus());
        validateDni(request.getDni(), authContext.getCurrentOrganizationId());

        Branch branch =
                branchRepository.findById(authContext.getCurrentBranchId())
                        .orElseThrow(() ->
                                new Exceptions("error.sedeNoEncontrada2", HttpStatus.NOT_FOUND)
                        );

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

        personRepository.save(person);

        PersonBranch personBranch = new PersonBranch();

        personBranch.setPerson(person);
        personBranch.setBranch(branch);
        personBranch.setStatus(StatusType.ACTIVE);
        personBranch.setStartDate(LocalDate.now());
        personBranch.setEndDate(null);

        personBranchRepository.save(personBranch);

        person.getBranchHistory().add(personBranch);

        return mapper.toResponse(person);
    }

    // =====================================================
    // UPDATE
    // =====================================================

    @Override
    @Transactional
    public OrgUserResponse update(UUID id, OrgUserUpdateRequest request) {

        Person person = findPersonOrThrow(id);

        validateAccess(person);
        validateContextMatchesActiveBranch(person);

        validateMaritalStatus(request.getMaritalStatus());

        if (request.getDni() != null && !request.getDni().equals(person.getDni())) {

            UUID organizationId =
                    requireActiveBranch(person).getBranch().getOrganization().getId();

            validateDniForUpdate(person.getId(), request.getDni(), organizationId);
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

        personRepository.save(person);

        return mapper.toResponse(person);
    }

    // =====================================================
    // GET BY ID
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public OrgUserResponse getById(UUID id) {

        Person person = findPersonOrThrow(id);

        validateAccess(person);

        return mapper.toResponse(person);
    }

    // =====================================================
    // VALIDACIONES / HELPERS
    // =====================================================

    private void assertCallerIsOrgOrBranchAdmin() {

        if (!authContext.isCurrentOrganizationAdmin()
                && !authContext.isCurrentBranchAdmin()) {

            throw new Exceptions(
                    "error.soloAdministradorOrganizacionSedePuedeGestionar3",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private void validateMaritalStatus(Object maritalStatus) {

        if (maritalStatus == null) {
            throw new Exceptions(
                    "error.estadoCivilObligatorio",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * El DNI es único por organización, no globalmente: la misma
     * persona (mismo DNI) puede existir en otra organización sin
     * problema, pero no dos veces dentro de la misma organización
     * sin importar la sede.
     */
    private void validateDni(String dni, UUID organizationId) {
        if (personRepository.existsByDniInOrganization(dni, organizationId)) {
            throw new Exceptions("error.dniRegistradoOrganizacion", HttpStatus.CONFLICT);
        }
    }

    private void validateDniForUpdate(UUID personId, String dni, UUID organizationId) {
        if (personRepository.existsByDniInOrganizationAndIdNot(dni, organizationId, personId)) {
            throw new Exceptions("error.dniRegistradoOrganizacion", HttpStatus.CONFLICT);
        }
    }

    private void validateAccess(Person person) {

        PersonBranch activeBranch = requireActiveBranch(person);

        UUID organizationId = activeBranch.getBranch().getOrganization().getId();
        UUID branchId = activeBranch.getBranch().getId();

        if (!authContext.canAccess(organizationId, branchId)) {

            throw new Exceptions(
                    "error.noTienePermisosAdministrarPersona",
                    HttpStatus.UNAUTHORIZED
            );
        }
    }

    /**
     * Se puede LISTAR/VER una persona desde cualquier sede de la
     * organización por la que alguna vez pasó, pero solo se puede
     * EDITARLA si el contexto actual (sede en la que se está
     * trabajando ahora mismo) coincide con su sede activa real.
     */
    private void validateContextMatchesActiveBranch(Person person) {

        PersonBranch activeBranch = requireActiveBranch(person);

        UUID contextBranchId = authContext.getCurrentBranchId();

        if (contextBranchId == null
                || !contextBranchId.equals(activeBranch.getBranch().getId())) {

            throw new Exceptions(
                    "error.soloPuedeEditarPersonasSedeActiva",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private PersonBranch requireActiveBranch(Person person) {

        return person.getBranchHistory()
                .stream()
                .filter(pb -> pb.getStatus() == StatusType.ACTIVE)
                .findFirst()
                .orElseThrow(() ->
                        new Exceptions(
                                "error.personaNoTieneSedeActiva",
                                HttpStatus.CONFLICT
                        )
                );
    }

    private Person findPersonOrThrow(UUID id) {

        return personRepository.findById(id)
                .orElseThrow(() ->
                        new Exceptions("error.personaNoEncontrada", HttpStatus.NOT_FOUND)
                );
    }

}
