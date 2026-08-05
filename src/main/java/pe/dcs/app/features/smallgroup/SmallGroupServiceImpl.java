package pe.dcs.app.features.smallgroup;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.Ministry;
import pe.dcs.app.entity.MinistryAssignment;
import pe.dcs.app.entity.MinistryRole;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.PersonBranch;
import pe.dcs.app.entity.SmallGroup;
import pe.dcs.app.entity.SmallGroupMember;
import pe.dcs.app.features.smallgroup.mapper.SmallGroupMapper;
import pe.dcs.app.features.smallgroup.request.SmallGroupFormRequest;
import pe.dcs.app.features.smallgroup.request.SmallGroupMemberFormRequest;
import pe.dcs.app.features.smallgroup.request.SmallGroupSearchRequest;
import pe.dcs.app.features.smallgroup.response.SmallGroupDetailResponse;
import pe.dcs.app.features.smallgroup.response.SmallGroupMemberResponse;
import pe.dcs.app.features.smallgroup.response.SmallGroupPersonSearchResponse;
import pe.dcs.app.features.smallgroup.response.SmallGroupSearchRowResponse;
import pe.dcs.app.features.smallgroup.service.SmallGroupService;
import pe.dcs.app.repository.BranchRepository;
import pe.dcs.app.repository.MembershipRepository;
import pe.dcs.app.repository.MinistryAssignmentRepository;
import pe.dcs.app.repository.MinistryRepository;
import pe.dcs.app.repository.MinistryRoleRepository;
import pe.dcs.app.repository.PersonRepository;
import pe.dcs.app.repository.SmallGroupMemberRepository;
import pe.dcs.app.repository.SmallGroupRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Grupos pequeños / células. A diferencia de Matrimonios
 * (admin-only), acá un org user puede liderar y gestionar su propio
 * grupo (ver SmallGroupAccessGuard) — y a diferencia de casi todo el
 * resto del sistema, los participantes NO tienen por qué ser
 * miembros ni tener registro alguno: SmallGroupMember admite
 * guestName/guestPhone en lugar de un vínculo a Person.
 *
 * El líder SÍ genera un efecto automático: mientras el grupo tenga
 * un líder vinculado a una Person con sede activa, su temporada
 * (startDate/endDate) se refleja como un servicio ministerial (ver
 * syncLeaderMinistryService) — igual criterio de "efecto automático
 * solo si aplica" que MarriageServiceImpl.syncMaritalStatus.
 */
@Service
@RequiredArgsConstructor
public class SmallGroupServiceImpl implements SmallGroupService {

    /**
     * Ministerio/rol de referencia generados de forma perezosa (find-
     * or-create) la primera vez que un grupo pequeño tiene un líder
     * vinculado — no se siembran en import.sql porque Ministry/
     * MinistryRole son catálogo libre gestionado por SYSTEM, y acá
     * solo necesitamos que existan con estos nombres exactos.
     * requiresActiveMembership=false porque, igual que el resto de
     * este feature, el líder no tiene por qué ser miembro.
     */
    private static final String GROUP_MINISTRY_CODE = "GRUPOS_PEQUENOS";
    private static final String GROUP_MINISTRY_NAME_ES = "Grupos Pequeños";
    private static final String GROUP_MINISTRY_NAME_EN = "Small Groups";
    private static final String GROUP_LEADER_ROLE_CODE = "LIDER_GRUPO_PEQUENO";
    private static final String GROUP_LEADER_ROLE_NAME_ES = "Líder de Grupo Pequeño";
    private static final String GROUP_LEADER_ROLE_NAME_EN = "Small Group Leader";

    private final SmallGroupRepository smallGroupRepository;
    private final SmallGroupMemberRepository smallGroupMemberRepository;
    private final PersonRepository personRepository;
    private final BranchRepository branchRepository;
    private final MembershipRepository membershipRepository;
    private final MinistryRepository ministryRepository;
    private final MinistryRoleRepository ministryRoleRepository;
    private final MinistryAssignmentRepository ministryAssignmentRepository;
    private final SmallGroupMapper mapper;
    private final AuthContext authContext;
    private final SmallGroupAccessGuard accessGuard;

    // =====================================================
    // SEARCH / GET
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SmallGroupSearchRowResponse> search(SmallGroupSearchRequest request) {

        accessGuard.assertCanUse();

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        Page<SmallGroup> page =
                smallGroupRepository.findAll(
                        SmallGroupSpecification.filter(request.getFilters(), authContext),
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(g -> mapper.toSearchRow(
                                g,
                                smallGroupMemberRepository.countByGroupIdAndStatus(g.getId(), StatusType.ACTIVE),
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
    public SmallGroupDetailResponse getById(UUID id) {

        SmallGroup group = findOrThrow(id);

        accessGuard.assertSameOrganization(group);

        List<SmallGroupMemberResponse> members =
                smallGroupMemberRepository.findByGroupIdOrderByJoinDateAsc(group.getId())
                        .stream()
                        .map(m -> mapper.toMemberResponse(m, isActiveMember(m.getPerson())))
                        .toList();

        return mapper.toDetailResponse(
                group,
                members,
                isActiveMember(group.getLeaderPerson()),
                accessGuard.canManage(group)
        );
    }

    // =====================================================
    // BUSCAR PERSONA POR DNI (líder o participante)
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public SmallGroupPersonSearchResponse findPersonByDni(String dni) {

        accessGuard.assertCanUse();

        UUID organizationId = authContext.getCurrentOrganizationId();

        if (organizationId == null) {
            throw new Exceptions(
                    "error.noTieneContextoOrganizacionActivo",
                    HttpStatus.FORBIDDEN
            );
        }

        if (dni == null || dni.isBlank()) {
            throw new Exceptions(
                    "error.elDniEsObligatorio",
                    HttpStatus.BAD_REQUEST
            );
        }

        Person person =
                personRepository.findByDniInOrganization(dni, organizationId)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.noEncontroNingunaPersonaDniOrganizacion",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        return new SmallGroupPersonSearchResponse(
                person.getId(),
                person.getName(),
                person.getLastname(),
                person.getDni(),
                isActiveMember(person)
        );
    }

    // =====================================================
    // CREATE / UPDATE
    // =====================================================

    @Override
    @Transactional
    public UUID create(SmallGroupFormRequest request) {

        accessGuard.assertCanCreate();

        validateForm(request);

        Branch branch = resolveBranch(request.getBranchId());

        SmallGroup group = new SmallGroup();
        group.setStatus(StatusType.ACTIVE);

        applyForm(group, request, branch);

        smallGroupRepository.save(group);

        syncLeaderMinistryService(group);

        smallGroupRepository.save(group);

        return group.getId();
    }

    @Override
    @Transactional
    public void update(UUID id, SmallGroupFormRequest request) {

        SmallGroup group = findOrThrow(id);

        accessGuard.assertSameOrganization(group);
        accessGuard.assertCanManage(group);

        validateForm(request);

        Branch branch =
                request.getBranchId() != null
                        ? resolveBranch(request.getBranchId())
                        : group.getBranch();

        applyForm(group, request, branch);

        syncLeaderMinistryService(group);

        smallGroupRepository.save(group);
    }

    // =====================================================
    // MIEMBROS
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public List<SmallGroupMemberResponse> listMembers(UUID groupId) {

        SmallGroup group = findOrThrow(groupId);

        accessGuard.assertSameOrganization(group);

        return smallGroupMemberRepository.findByGroupIdOrderByJoinDateAsc(groupId)
                .stream()
                .map(m -> mapper.toMemberResponse(m, isActiveMember(m.getPerson())))
                .toList();
    }

    /**
     * Un participante puede o no tener Person vinculada — si
     * request.personId viene informado se usa ese vínculo (sin
     * importar si es miembro o no, ver clase de nivel superior); si
     * no, se guarda solo con guestName/guestPhone. No se crea una
     * Person nueva en ningún caso.
     */
    @Override
    @Transactional
    public void addMember(UUID groupId, SmallGroupMemberFormRequest request) {

        SmallGroup group = findOrThrow(groupId);

        accessGuard.assertSameOrganization(group);
        accessGuard.assertCanManage(group);

        validateMemberForm(request);

        Person person =
                request.getPersonId() != null
                        ? findPersonOrThrow(request.getPersonId())
                        : null;

        if (person != null
                && smallGroupMemberRepository.existsByGroupIdAndPersonIdAndStatus(
                        groupId, person.getId(), StatusType.ACTIVE
                )) {

            throw new Exceptions(
                    "error.personaParticipanteActivoGrupo",
                    HttpStatus.BAD_REQUEST
            );
        }

        SmallGroupMember member = new SmallGroupMember();
        member.setGroup(group);
        member.setPerson(person);
        member.setGuestName(person == null ? request.getGuestName() : null);
        member.setGuestPhone(person == null ? request.getGuestPhone() : null);
        member.setJoinDate(
                request.getJoinDate() != null
                        ? request.getJoinDate()
                        : LocalDate.now()
        );
        member.setStatus(StatusType.ACTIVE);

        smallGroupMemberRepository.save(member);
    }

    @Override
    @Transactional
    public void removeMember(UUID groupId, UUID memberId) {

        SmallGroup group = findOrThrow(groupId);

        accessGuard.assertSameOrganization(group);
        accessGuard.assertCanManage(group);

        SmallGroupMember member =
                smallGroupMemberRepository.findById(memberId)
                        .orElseThrow(() ->
                                new Exceptions("error.participanteNoEncontrado", HttpStatus.NOT_FOUND)
                        );

        if (!member.getGroup().getId().equals(groupId)) {
            throw new Exceptions(
                    "error.participanteNoPerteneceGrupo",
                    HttpStatus.BAD_REQUEST
            );
        }

        smallGroupMemberRepository.delete(member);
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private void applyForm(SmallGroup group, SmallGroupFormRequest request, Branch branch) {

        group.setName(request.getName());
        group.setDescription(request.getDescription());

        group.setLeaderName(request.getLeaderName());
        group.setLeaderPerson(
                request.getLeaderPersonId() != null
                        ? findPersonOrThrow(request.getLeaderPersonId())
                        : null
        );

        group.setMeetingDay(request.getMeetingDay());
        group.setMeetingTime(request.getMeetingTime());
        group.setLocation(request.getLocation());
        group.setCategory(request.getCategory());

        group.setStartDate(request.getStartDate());
        group.setEndDate(request.getEndDate());
        group.setTopic(request.getTopic());

        if (request.getStatus() != null) {
            group.setStatus(request.getStatus());
        }

        group.setBranch(branch);
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
     * org admin elige libremente la sede; cualquier otro rol
     * (branch admin u org user delegado) queda ligado a su propia
     * sede actual.
     */
    private Branch resolveBranch(UUID branchId) {

        if (!authContext.isCurrentOrganizationAdmin()) {

            return branchRepository.findById(
                    authContext.getCurrentBranchId()
            ).orElseThrow(() ->
                    new Exceptions("error.sedeNoEncontrada", HttpStatus.NOT_FOUND)
            );
        }

        if (branchId == null) {
            throw new Exceptions(
                    "error.debeSeleccionarSedeGrupo",
                    HttpStatus.BAD_REQUEST
            );
        }

        return branchRepository.findById(branchId)
                .orElseThrow(() ->
                        new Exceptions("error.sedeNoEncontrada", HttpStatus.NOT_FOUND)
                );
    }

    private void validateForm(SmallGroupFormRequest request) {

        if (request.getName() == null || request.getName().isBlank()) {
            throw new Exceptions(
                    "error.nombreGrupoObligatorio",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (request.getLeaderPersonId() == null
                && (request.getLeaderName() == null || request.getLeaderName().isBlank())) {

            throw new Exceptions(
                    "error.liderGrupoObligatorio",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (request.getStartDate() == null) {
            throw new Exceptions(
                    "error.fechaInicioTemporadaObligatoria",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (request.getEndDate() != null
                && request.getEndDate().isBefore(request.getStartDate())) {

            throw new Exceptions(
                    "error.fechaFinTemporadaNoPuedeSer",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void validateMemberForm(SmallGroupMemberFormRequest request) {

        if (request.getPersonId() == null
                && (request.getGuestName() == null || request.getGuestName().isBlank())) {

            throw new Exceptions(
                    "error.nombreParticipanteObligatorio",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * Refleja el liderazgo del grupo como servicio ministerial
     * (MinistryAssignment) mientras dure la temporada. Un único
     * registro por grupo (group.ministryAssignment), reutilizado y
     * actualizado en cada save en vez de crear uno nuevo por cada
     * cambio de líder o de fechas.
     *
     * - Sin líder vinculado a una Person: si había un registro
     *   abierto (endDate null) se cierra con la fecha de hoy y se
     *   desvincula del grupo — no se borra, queda como historial.
     * - Con líder vinculado pero SIN sede activa: no se puede
     *   atribuir sede al servicio ministerial, así que no se toca
     *   nada (mismo criterio que Marriage: "solo si cumple los
     *   requisitos, si no, solo se registra el nombre").
     * - Con líder vinculado y sede activa: crea o actualiza el
     *   registro con las fechas/tema actuales del grupo. No se
     *   valida solapamiento con otros ministerios (a diferencia de
     *   MinistryAssignmentServiceImpl) porque liderar más de un
     *   grupo pequeño a la vez es un caso legítimo.
     */
    private void syncLeaderMinistryService(SmallGroup group) {

        Person leader = group.getLeaderPerson();

        if (leader == null) {

            MinistryAssignment existing = group.getMinistryAssignment();

            if (existing != null && existing.getEndDate() == null) {
                existing.setEndDate(LocalDate.now());
                ministryAssignmentRepository.save(existing);
            }

            group.setMinistryAssignment(null);
            return;
        }

        PersonBranch activeBranch = findActiveBranch(leader);

        if (activeBranch == null) {
            return;
        }

        Ministry ministry = findOrCreateGroupMinistry();
        MinistryRole role = findOrCreateLeaderRole(ministry);

        MinistryAssignment assignment =
                group.getMinistryAssignment() != null
                        ? group.getMinistryAssignment()
                        : new MinistryAssignment();

        assignment.setPerson(leader);
        assignment.setMinistry(ministry);
        assignment.setMinistryRole(role);
        assignment.setStartDate(group.getStartDate());
        assignment.setEndDate(group.getEndDate());
        assignment.setReason("Liderazgo del grupo pequeño: " + group.getName());
        assignment.setObservation(group.getTopic());
        assignment.setBranch(activeBranch.getBranch());

        ministryAssignmentRepository.save(assignment);

        group.setMinistryAssignment(assignment);
    }

    private PersonBranch findActiveBranch(Person person) {

        return person.getBranchHistory()
                .stream()
                .filter(pb -> pb.getStatus() == StatusType.ACTIVE)
                .findFirst()
                .orElse(null);
    }

    private Ministry findOrCreateGroupMinistry() {

        return ministryRepository.findByCode(GROUP_MINISTRY_CODE)
                .orElseGet(() -> {

                    Ministry ministry = new Ministry();
                    ministry.setCode(GROUP_MINISTRY_CODE);
                    ministry.setNameEs(GROUP_MINISTRY_NAME_ES);
                    ministry.setNameEn(GROUP_MINISTRY_NAME_EN);
                    ministry.setDescription(
                            "Generado automáticamente para registrar el liderazgo de Grupos Pequeños / Células como servicio ministerial."
                    );
                    ministry.setStatus(StatusType.ACTIVE);
                    ministry.setRequiresActiveMembership(false);

                    return ministryRepository.save(ministry);
                });
    }

    private MinistryRole findOrCreateLeaderRole(Ministry ministry) {

        return ministryRoleRepository.findByMinistryIdAndCode(ministry.getId(), GROUP_LEADER_ROLE_CODE)
                .orElseGet(() -> {

                    MinistryRole role = new MinistryRole();
                    role.setCode(GROUP_LEADER_ROLE_CODE);
                    role.setNameEs(GROUP_LEADER_ROLE_NAME_ES);
                    role.setNameEn(GROUP_LEADER_ROLE_NAME_EN);
                    role.setMinistry(ministry);
                    role.setStatus(StatusType.ACTIVE);
                    role.setRequiresActiveMembership(false);

                    return ministryRoleRepository.save(role);
                });
    }

    private SmallGroup findOrThrow(UUID id) {

        return smallGroupRepository.findById(id)
                .orElseThrow(() ->
                        new Exceptions(
                                "error.grupoPequenoNoEncontrado",
                                HttpStatus.NOT_FOUND
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
