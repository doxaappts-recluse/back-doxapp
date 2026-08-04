package pe.dcs.app.features.familygroup;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.FamilyGroup;
import pe.dcs.app.entity.FamilyMember;
import pe.dcs.app.entity.Person;
import pe.dcs.app.features.familygroup.mapper.FamilyGroupMapper;
import pe.dcs.app.features.familygroup.request.FamilyGroupFormRequest;
import pe.dcs.app.features.familygroup.request.FamilyGroupSearchRequest;
import pe.dcs.app.features.familygroup.request.FamilyMemberFormRequest;
import pe.dcs.app.features.familygroup.response.FamilyGroupDetailResponse;
import pe.dcs.app.features.familygroup.response.FamilyGroupPersonSearchResponse;
import pe.dcs.app.features.familygroup.response.FamilyGroupSearchRowResponse;
import pe.dcs.app.features.familygroup.response.FamilyMemberResponse;
import pe.dcs.app.features.familygroup.service.FamilyGroupService;
import pe.dcs.app.repository.BranchRepository;
import pe.dcs.app.repository.FamilyGroupRepository;
import pe.dcs.app.repository.FamilyMemberRepository;
import pe.dcs.app.repository.MembershipRepository;
import pe.dcs.app.repository.PersonRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.FamilyRole;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Grupo Familiar: realce gratuito de Gestión de Miembros (no es un
 * módulo comercial propio — ver import.sql), que agrupa Person con un
 * rol (jefe de hogar, cónyuge, hijo, otro). A diferencia de
 * SmallGroupMember, acá SIEMPRE hay una Person vinculada (no hay
 * invitados de solo nombre) y cada Person pertenece a un solo grupo a
 * la vez (ver constraint unique en FamilyMember.person).
 *
 * Efecto automático: al registrar/editar un Matrimonio con al menos
 * un cónyuge vinculado a Person, se crea/actualiza el grupo familiar
 * correspondiente (ver syncFromMarriage, invocado desde
 * MarriageServiceImpl) — hijos u otros parientes siempre se agregan a
 * mano, no hay fuente de datos automática para ellos.
 */
@Service
@RequiredArgsConstructor
public class FamilyGroupServiceImpl implements FamilyGroupService {

    private final FamilyGroupRepository familyGroupRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final PersonRepository personRepository;
    private final BranchRepository branchRepository;
    private final MembershipRepository membershipRepository;
    private final FamilyGroupMapper mapper;
    private final AuthContext authContext;
    private final FamilyGroupAccessGuard accessGuard;

    // =====================================================
    // SEARCH / GET
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FamilyGroupSearchRowResponse> search(FamilyGroupSearchRequest request) {

        accessGuard.assertCanUse();

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        Page<FamilyGroup> page =
                familyGroupRepository.findAll(
                        FamilyGroupSpecification.filter(request.getFilters(), authContext),
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(g -> mapper.toSearchRow(
                                g,
                                familyMemberRepository.countByFamilyGroupId(g.getId()),
                                accessGuard.canManage(g),
                                showAudit
                        ))
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
    @Transactional(readOnly = true)
    public FamilyGroupDetailResponse getById(UUID id) {

        FamilyGroup group = findOrThrow(id);

        accessGuard.assertSameOrganization(group);

        List<FamilyMemberResponse> members =
                familyMemberRepository.findByFamilyGroupIdOrderByRoleAsc(group.getId())
                        .stream()
                        .map(m -> mapper.toMemberResponse(m, isActiveMember(m.getPerson())))
                        .toList();

        return mapper.toDetailResponse(
                group,
                members,
                accessGuard.canManage(group)
        );
    }

    // =====================================================
    // BUSCAR PERSONA POR DNI (jefe de hogar o miembro)
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public FamilyGroupPersonSearchResponse findPersonByDni(String dni) {

        accessGuard.assertCanUse();

        UUID organizationId = authContext.getCurrentOrganizationId();

        if (organizationId == null) {
            throw new Exceptions(
                    "No tiene un contexto de organización activo.",
                    HttpStatus.FORBIDDEN
            );
        }

        if (dni == null || dni.isBlank()) {
            throw new Exceptions(
                    "El DNI es obligatorio.",
                    HttpStatus.BAD_REQUEST
            );
        }

        Person person =
                personRepository.findByDniInOrganization(dni, organizationId)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "No se encontró ninguna persona con ese DNI en la organización.",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        Optional<FamilyMember> existing = familyMemberRepository.findByPersonId(person.getId());

        return new FamilyGroupPersonSearchResponse(
                person.getId(),
                person.getName(),
                person.getLastname(),
                person.getDni(),
                isActiveMember(person),
                existing.map(m -> m.getFamilyGroup().getId()).orElse(null),
                existing.map(m -> m.getFamilyGroup().getName()).orElse(null)
        );
    }

    // =====================================================
    // CREATE / UPDATE
    // =====================================================

    @Override
    @Transactional
    public UUID create(FamilyGroupFormRequest request) {

        accessGuard.assertCanCreate();

        if (request.getHeadPersonId() == null) {
            throw new Exceptions(
                    "Debe seleccionar el jefe de hogar inicial del grupo familiar.",
                    HttpStatus.BAD_REQUEST
            );
        }

        Person head = findPersonOrThrow(request.getHeadPersonId());

        assertPersonAvailable(head);

        Branch branch = resolveBranch(request.getBranchId());

        FamilyGroup group = new FamilyGroup();
        group.setStatus(StatusType.ACTIVE);
        group.setBranch(branch);
        group.setName(resolveName(request.getName(), head));
        group.setObservations(request.getObservations());

        familyGroupRepository.save(group);

        saveMember(group, head, FamilyRole.HEAD_OF_HOUSEHOLD);

        return group.getId();
    }

    @Override
    @Transactional
    public void update(UUID id, FamilyGroupFormRequest request) {

        FamilyGroup group = findOrThrow(id);

        accessGuard.assertSameOrganization(group);
        accessGuard.assertCanManage(group);

        if (request.getName() != null && !request.getName().isBlank()) {
            group.setName(request.getName());
        }

        group.setObservations(request.getObservations());

        if (request.getStatus() != null) {
            group.setStatus(request.getStatus());
        }

        if (request.getBranchId() != null) {
            group.setBranch(resolveBranch(request.getBranchId()));
        }

        familyGroupRepository.save(group);
    }

    // =====================================================
    // MIEMBROS
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public List<FamilyMemberResponse> listMembers(UUID groupId) {

        FamilyGroup group = findOrThrow(groupId);

        accessGuard.assertSameOrganization(group);

        return familyMemberRepository.findByFamilyGroupIdOrderByRoleAsc(groupId)
                .stream()
                .map(m -> mapper.toMemberResponse(m, isActiveMember(m.getPerson())))
                .toList();
    }

    @Override
    @Transactional
    public void addMember(UUID groupId, FamilyMemberFormRequest request) {

        FamilyGroup group = findOrThrow(groupId);

        accessGuard.assertSameOrganization(group);
        accessGuard.assertCanManage(group);

        validateMemberForm(request);

        Person person = findPersonOrThrow(request.getPersonId());

        assertPersonAvailable(person);

        if (request.getRole() == FamilyRole.HEAD_OF_HOUSEHOLD) {
            assertNoExistingHead(group, null);
        }

        saveMember(group, person, request.getRole());
    }

    @Override
    @Transactional
    public void updateMemberRole(UUID groupId, UUID memberId, FamilyMemberFormRequest request) {

        FamilyGroup group = findOrThrow(groupId);

        accessGuard.assertSameOrganization(group);
        accessGuard.assertCanManage(group);

        FamilyMember member = findMemberOrThrow(memberId, groupId);

        if (request.getRole() == null) {
            throw new Exceptions(
                    "El rol es obligatorio.",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (request.getRole() == FamilyRole.HEAD_OF_HOUSEHOLD) {
            assertNoExistingHead(group, member.getId());
        }

        member.setRole(request.getRole());

        if (request.getJoinDate() != null) {
            member.setJoinDate(request.getJoinDate());
        }

        familyMemberRepository.save(member);
    }

    @Override
    @Transactional
    public void removeMember(UUID groupId, UUID memberId) {

        FamilyGroup group = findOrThrow(groupId);

        accessGuard.assertSameOrganization(group);
        accessGuard.assertCanManage(group);

        FamilyMember member = findMemberOrThrow(memberId, groupId);

        familyMemberRepository.delete(member);
    }

    // =====================================================
    // AUTO-DETECCIÓN DESDE MATRIMONIOS
    // =====================================================

    /**
     * Best-effort: cualquier error queda contenido acá — NUNCA debe
     * romper el flujo de registrar/editar un matrimonio (ver
     * MarriageServiceImpl).
     */
    @Override
    @Transactional
    public void syncFromMarriage(Person spouse1, Person spouse2, Branch branch) {

        try {
            doSyncFromMarriage(spouse1, spouse2, branch);
        } catch (Exception ignored) {
            // No bloquea la creación/edición del matrimonio.
        }
    }

    private void doSyncFromMarriage(Person spouse1, Person spouse2, Branch branch) {

        if (spouse1 == null && spouse2 == null) {
            return;
        }

        Optional<FamilyMember> member1 =
                spouse1 != null
                        ? familyMemberRepository.findByPersonId(spouse1.getId())
                        : Optional.empty();

        Optional<FamilyMember> member2 =
                spouse2 != null
                        ? familyMemberRepository.findByPersonId(spouse2.getId())
                        : Optional.empty();

        if (member1.isPresent() && member2.isPresent()) {
            // Ambos ya tienen grupo (el mismo u otro distinto) — no
            // se fusiona nada automáticamente, queda para el admin.
            return;
        }

        if (member1.isPresent()) {
            if (spouse2 != null) {
                saveMember(member1.get().getFamilyGroup(), spouse2, FamilyRole.SPOUSE);
            }
            return;
        }

        if (member2.isPresent()) {
            if (spouse1 != null) {
                saveMember(member2.get().getFamilyGroup(), spouse1, FamilyRole.SPOUSE);
            }
            return;
        }

        // Ninguno tiene grupo familiar todavía: se crea uno nuevo.
        Person reference = spouse1 != null ? spouse1 : spouse2;

        FamilyGroup group = new FamilyGroup();
        group.setStatus(StatusType.ACTIVE);
        group.setBranch(branch);
        group.setName(resolveName(null, reference));

        familyGroupRepository.save(group);

        if (spouse1 != null) {
            saveMember(group, spouse1, FamilyRole.HEAD_OF_HOUSEHOLD);
        }

        if (spouse2 != null) {
            saveMember(
                    group,
                    spouse2,
                    spouse1 != null ? FamilyRole.SPOUSE : FamilyRole.HEAD_OF_HOUSEHOLD
            );
        }
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private void saveMember(FamilyGroup group, Person person, FamilyRole role) {

        FamilyMember member = new FamilyMember();
        member.setFamilyGroup(group);
        member.setPerson(person);
        member.setRole(role);
        member.setJoinDate(LocalDate.now());

        familyMemberRepository.save(member);
    }

    private void assertPersonAvailable(Person person) {

        if (familyMemberRepository.existsByPersonId(person.getId())) {
            throw new Exceptions(
                    "Esta persona ya pertenece a otro grupo familiar. Quítela de ese grupo antes de agregarla acá.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void assertNoExistingHead(FamilyGroup group, UUID ignoreMemberId) {

        boolean alreadyHasHead =
                familyMemberRepository.findByFamilyGroupIdOrderByRoleAsc(group.getId())
                        .stream()
                        .anyMatch(m ->
                                m.getRole() == FamilyRole.HEAD_OF_HOUSEHOLD
                                        && (ignoreMemberId == null || !m.getId().equals(ignoreMemberId))
                        );

        if (alreadyHasHead) {
            throw new Exceptions(
                    "Este grupo familiar ya tiene un jefe de hogar.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private String resolveName(String requestedName, Person reference) {

        if (requestedName != null && !requestedName.isBlank()) {
            return requestedName;
        }

        if (reference == null) {
            return "Familia";
        }

        String lastname =
                reference.getLastname() != null && !reference.getLastname().isBlank()
                        ? reference.getLastname()
                        : reference.getName();

        return "Familia " + lastname;
    }

    private boolean isActiveMember(Person person) {

        return person != null
                && membershipRepository.existsByPersonIdAndCurrentTrueAndStatus(
                        person.getId(),
                        StatusType.ACTIVE
                );
    }

    /**
     * Igual criterio que MarriageServiceImpl.resolveBranch: solo el
     * org admin elige libremente la sede; cualquier otro rol (branch
     * admin u org user delegado) queda ligado a su propia sede
     * actual.
     */
    private Branch resolveBranch(UUID branchId) {

        if (!authContext.isCurrentOrganizationAdmin()) {

            return branchRepository.findById(
                    authContext.getCurrentBranchId()
            ).orElseThrow(() ->
                    new Exceptions("Sede no encontrada", HttpStatus.NOT_FOUND)
            );
        }

        if (branchId == null) {
            throw new Exceptions(
                    "Debe seleccionar la sede del grupo familiar.",
                    HttpStatus.BAD_REQUEST
            );
        }

        return branchRepository.findById(branchId)
                .orElseThrow(() ->
                        new Exceptions("Sede no encontrada", HttpStatus.NOT_FOUND)
                );
    }

    private void validateMemberForm(FamilyMemberFormRequest request) {

        if (request.getPersonId() == null) {
            throw new Exceptions(
                    "Debe seleccionar la persona a agregar.",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (request.getRole() == null) {
            throw new Exceptions(
                    "El rol es obligatorio.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private FamilyGroup findOrThrow(UUID id) {

        return familyGroupRepository.findById(id)
                .orElseThrow(() ->
                        new Exceptions(
                                "Grupo familiar no encontrado.",
                                HttpStatus.NOT_FOUND
                        )
                );
    }

    private FamilyMember findMemberOrThrow(UUID memberId, UUID groupId) {

        FamilyMember member =
                familyMemberRepository.findById(memberId)
                        .orElseThrow(() ->
                                new Exceptions("Miembro no encontrado.", HttpStatus.NOT_FOUND)
                        );

        if (!member.getFamilyGroup().getId().equals(groupId)) {
            throw new Exceptions(
                    "Este miembro no pertenece a este grupo familiar.",
                    HttpStatus.BAD_REQUEST
            );
        }

        return member;
    }

    private Person findPersonOrThrow(UUID id) {

        return personRepository.findById(id)
                .orElseThrow(() ->
                        new Exceptions("Persona no encontrada.", HttpStatus.NOT_FOUND)
                );
    }
}
