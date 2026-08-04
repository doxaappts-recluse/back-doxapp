package pe.dcs.app.features.baptism;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Baptism;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.PersonBranch;
import pe.dcs.app.features.baptism.mapper.BaptismMapper;
import pe.dcs.app.features.baptism.request.BaptismFormRequest;
import pe.dcs.app.features.baptism.request.BaptismSearchRequest;
import pe.dcs.app.features.baptism.response.BaptismContextResponse;
import pe.dcs.app.features.baptism.response.BaptismSearchRowResponse;
import pe.dcs.app.features.baptism.service.BaptismService;
import pe.dcs.app.features.visibility.VisibilityGuard;
import pe.dcs.app.repository.BaptismRepository;
import pe.dcs.app.repository.PersonRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.resolveSort.PersonSort;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.util.UUID;

/**
 * Bautizo de una persona: registro único por persona (ver
 * constraint uk_baptism_user en Baptism), no es un historial
 * como Membership. Crear falla si ya existe uno; editar
 * modifica el registro existente en el sitio.
 */
@Service
@RequiredArgsConstructor
public class BaptismServiceImpl implements BaptismService {

    private final PersonRepository personRepository;
    private final BaptismRepository baptismRepository;
    private final BaptismMapper mapper;
    private final AuthContext authContext;
    private final VisibilityGuard visibilityGuard;

    private static final String MODULE_CODE = "BAPTISM";

    // =====================================================
    // SEARCH
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BaptismSearchRowResponse> search(BaptismSearchRequest request) {

        assertCallerCanManage();

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts(),
                        PersonSort::resolvePath
                );

        Page<Person> page =
                personRepository.findAll(
                        BaptismSpecification.filter(request, authContext),
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(person -> {

                            Baptism baptism =
                                    baptismRepository
                                            .findByUserId(person.getId())
                                            .orElse(null);

                            boolean visible =
                                    baptism == null
                                            || visibilityGuard.canView(
                                                    baptism.getBranch(),
                                                    person.getId(),
                                                    MODULE_CODE
                                            );

                            return mapper.toSearchRow(person, baptism, showAudit, visible);
                        })
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
    // GET CURRENT
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public BaptismContextResponse getCurrent(UUID userId) {

        Person person = findPersonOrThrow(userId);

        validateAccess(person);

        Baptism baptism =
                baptismRepository
                        .findByUserId(userId)
                        .orElse(null);

        boolean visible =
                baptism == null
                        || visibilityGuard.canView(baptism.getBranch(), userId, MODULE_CODE);

        return mapper.toContextResponse(person, baptism, visible);
    }

    // =====================================================
    // CREATE (falla si ya existe uno para la persona)
    // =====================================================

    @Override
    @Transactional
    public void create(UUID userId, BaptismFormRequest request) {

        Person person = findPersonOrThrow(userId);

        PersonBranch activeBranch = validateAccess(person);

        validateForm(request);

        if (baptismRepository.existsByUserId(userId)) {

            throw new Exceptions(
                    "La persona ya tiene un registro de bautizo. Edítalo en vez de crear uno nuevo.",
                    HttpStatus.CONFLICT
            );
        }

        Baptism baptism = new Baptism();

        baptism.setUser(person);
        baptism.setBaptismDate(request.getBaptismDate());
        baptism.setChurchName(request.getChurchName());
        baptism.setPastorName(request.getPastorName());
        baptism.setCity(request.getCity());
        baptism.setVerified(request.isVerified());
        baptism.setObservations(request.getObservations());

        /*
         * Sede "dueña" del registro. Ver Membership.branch en
         * MembershipServiceImpl — mismo propósito.
         */
        baptism.setBranch(activeBranch.getBranch());

        baptismRepository.save(baptism);
    }

    // =====================================================
    // UPDATE (edita el único registro existente)
    // =====================================================

    @Override
    @Transactional
    public void update(UUID userId, UUID baptismId, BaptismFormRequest request) {

        Person person = findPersonOrThrow(userId);

        validateAccess(person);

        validateForm(request);

        Baptism baptism =
                baptismRepository.findById(baptismId)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Registro de bautizo no encontrado.",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (baptism.getUser() == null
                || !baptism.getUser().getId().equals(userId)) {

            throw new Exceptions(
                    "El registro de bautizo no pertenece a esta persona.",
                    HttpStatus.BAD_REQUEST
            );
        }

        baptism.setBaptismDate(request.getBaptismDate());
        baptism.setChurchName(request.getChurchName());
        baptism.setPastorName(request.getPastorName());
        baptism.setCity(request.getCity());
        baptism.setVerified(request.isVerified());
        baptism.setObservations(request.getObservations());

        baptismRepository.save(baptism);
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private void validateForm(BaptismFormRequest request) {

        if (request.getBaptismDate() == null) {
            throw new Exceptions(
                    "La fecha de bautizo es obligatoria.",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (request.getChurchName() == null || request.getChurchName().isBlank()) {
            throw new Exceptions(
                    "La iglesia donde se bautizó es obligatoria.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void assertCallerCanManage() {
        authContext.assertCanManageCurrent("No tiene permisos para gestionar bautizos.");
    }

    private PersonBranch validateAccess(Person person) {

        PersonBranch activeBranch =
                person.getBranchHistory()
                        .stream()
                        .filter(pb -> pb.getStatus() == StatusType.ACTIVE)
                        .findFirst()
                        .orElse(null);

        if (activeBranch == null) {
            throw new Exceptions(
                    "La persona no tiene una sede activa.",
                    HttpStatus.CONFLICT
            );
        }

        UUID organizationId =
                activeBranch.getBranch().getOrganization().getId();

        UUID branchId =
                activeBranch.getBranch().getId();

        if (!authContext.canAccess(organizationId, branchId)) {

            throw new Exceptions(
                    "No tiene permisos para gestionar el bautizo de esta persona.",
                    HttpStatus.UNAUTHORIZED
            );
        }

        return activeBranch;
    }

    private Person findPersonOrThrow(UUID id) {

        return personRepository.findById(id)
                .orElseThrow(() ->
                        new Exceptions(
                                "Persona no encontrada.",
                                HttpStatus.NOT_FOUND
                        )
                );
    }

}
