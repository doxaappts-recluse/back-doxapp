package pe.dcs.app.features.marriage;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.FinancialMovement;
import pe.dcs.app.entity.Marriage;
import pe.dcs.app.entity.Person;
import pe.dcs.app.features.familygroup.service.FamilyGroupService;
import pe.dcs.app.features.finance.FinancialAccessGuard;
import pe.dcs.app.features.marriage.mapper.MarriageMapper;
import pe.dcs.app.features.marriage.request.MarriageFormRequest;
import pe.dcs.app.features.marriage.request.MarriageSearchRequest;
import pe.dcs.app.features.marriage.response.MarriageDetailResponse;
import pe.dcs.app.features.marriage.response.MarriageSearchRowResponse;
import pe.dcs.app.features.marriage.response.MarriageSpouseSearchResponse;
import pe.dcs.app.features.marriage.service.MarriageService;
import pe.dcs.app.repository.BranchRepository;
import pe.dcs.app.repository.FinancialMovementRepository;
import pe.dcs.app.repository.MarriageRepository;
import pe.dcs.app.repository.MembershipRepository;
import pe.dcs.app.repository.PersonRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.MaritalStatusType;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.finance.FinancialMovementCategory;
import pe.dcs.app.util.enums.finance.FinancialMovementStatus;
import pe.dcs.app.util.enums.finance.FinancialMovementType;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Matrimonios realizados en la iglesia. A diferencia de Bautizo (1:1
 * con una Person ya existente), acá los dos cónyuges pueden o no
 * existir como Person — se buscan por DNI (findSpouseByDni) y, si
 * aparecen, se guarda el vínculo; si no, el registro queda solo con
 * el nombre en texto libre (público general que no es miembro).
 *
 * Dos efectos automáticos al crear/editar:
 * - Si un cónyuge vinculado tiene una membresía ACTIVA (no alcanza
 *   con ser Person), se actualiza su Person.maritalStatus a MARRIED
 *   (ver syncMaritalStatus).
 * - Si se informa feeAmount, se crea/actualiza el FinancialMovement
 *   (categoría SERVICE_FEE) enlazado — la plata institucional vive
 *   en Movimientos, no duplicada acá (ver syncFinancialMovement).
 */
@Service
@RequiredArgsConstructor
public class MarriageServiceImpl implements MarriageService {

    private final MarriageRepository marriageRepository;
    private final PersonRepository personRepository;
    private final BranchRepository branchRepository;
    private final MembershipRepository membershipRepository;
    private final FinancialMovementRepository financialMovementRepository;
    private final MarriageMapper mapper;
    private final AuthContext authContext;
    private final FinancialAccessGuard financialAccessGuard;
    private final FamilyGroupService familyGroupService;

    // =====================================================
    // SEARCH / GET
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MarriageSearchRowResponse> search(MarriageSearchRequest request) {

        authContext.assertCanManageCurrent("error.noTienePermisosVerMatrimonios");

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        Page<Marriage> page =
                marriageRepository.findAll(
                        MarriageSpecification.filter(request.getFilters(), authContext),
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(m -> mapper.toSearchRow(m, showAudit))
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
    public MarriageDetailResponse getById(UUID id) {

        Marriage marriage = findOrThrow(id);

        assertSameOrganization(marriage.getBranch());

        return mapper.toDetailResponse(
                marriage,
                isActiveMember(marriage.getSpouse1Person()),
                isActiveMember(marriage.getSpouse2Person())
        );
    }

    // =====================================================
    // BUSCAR CONYUGE POR DNI
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public MarriageSpouseSearchResponse findSpouseByDni(String dni) {

        authContext.assertCanManageCurrent("error.noTienePermisosGestionarMatrimonios");

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

        return new MarriageSpouseSearchResponse(
                person.getId(),
                person.getName(),
                person.getLastname(),
                person.getDni(),
                isActiveMember(person)
        );
    }

    // =====================================================
    // CREATE
    // =====================================================

    @Override
    @Transactional
    public void create(MarriageFormRequest request) {

        authContext.assertCanManageCurrent("error.noTienePermisosGestionarMatrimonios");

        validateForm(request);

        Branch branch = resolveBranch(request.getBranchId());

        Marriage marriage = new Marriage();

        applyForm(marriage, request, branch);

        marriageRepository.save(marriage);

        syncMaritalStatus(marriage);
        syncFinancialMovement(marriage, request);
        syncFamilyGroup(marriage);

        marriageRepository.save(marriage);
    }

    // =====================================================
    // UPDATE
    // =====================================================

    @Override
    @Transactional
    public void update(UUID id, MarriageFormRequest request) {

        authContext.assertCanManageCurrent("error.noTienePermisosGestionarMatrimonios");

        validateForm(request);

        Marriage marriage = findOrThrow(id);

        assertSameOrganization(marriage.getBranch());

        Branch branch =
                request.getBranchId() != null
                        ? resolveBranch(request.getBranchId())
                        : marriage.getBranch();

        applyForm(marriage, request, branch);

        syncMaritalStatus(marriage);
        syncFinancialMovement(marriage, request);
        syncFamilyGroup(marriage);

        marriageRepository.save(marriage);
    }

    // =====================================================
    // HELPERS
    // =====================================================

    /**
     * Grupo Familiar (realce gratuito de Gestión de Miembros, ver
     * FamilyGroupServiceImpl): si al menos un cónyuge está vinculado
     * a Person, crea/actualiza automáticamente el grupo familiar
     * correspondiente. Diseñado para nunca lanzar excepción — no debe
     * romper el registro del matrimonio en ningún caso.
     */
    private void syncFamilyGroup(Marriage marriage) {

        familyGroupService.syncFromMarriage(
                marriage.getSpouse1Person(),
                marriage.getSpouse2Person(),
                marriage.getBranch()
        );
    }

    private void applyForm(Marriage marriage, MarriageFormRequest request, Branch branch) {

        marriage.setSpouse1Name(request.getSpouse1Name());
        marriage.setSpouse1Dni(request.getSpouse1Dni());
        marriage.setSpouse1Person(
                request.getSpouse1PersonId() != null
                        ? findPersonOrThrow(request.getSpouse1PersonId())
                        : null
        );

        marriage.setSpouse2Name(request.getSpouse2Name());
        marriage.setSpouse2Dni(request.getSpouse2Dni());
        marriage.setSpouse2Person(
                request.getSpouse2PersonId() != null
                        ? findPersonOrThrow(request.getSpouse2PersonId())
                        : null
        );

        marriage.setMarriageDate(request.getMarriageDate());
        marriage.setChurchName(request.getChurchName());
        marriage.setPastorName(request.getPastorName());
        marriage.setCity(request.getCity());
        marriage.setVerified(request.isVerified());
        marriage.setObservations(request.getObservations());
        marriage.setBranch(branch);
    }

    /**
     * Solo actualiza maritalStatus si el cónyuge está vinculado a
     * una Person Y esa Person tiene una membresía ACTIVA — no
     * alcanza con existir como Person (podría ser alguien que solo
     * asistió a un evento alguna vez). Si no es miembro activo (o no
     * se encontró Persona), no se toca nada: el nombre queda
     * guardado en el registro de matrimonio nada más, como pidió el
     * usuario ("solo si es miembro, si no, solo registrar").
     */
    private void syncMaritalStatus(Marriage marriage) {

        syncMaritalStatusForSpouse(marriage.getSpouse1Person());
        syncMaritalStatusForSpouse(marriage.getSpouse2Person());
    }

    private void syncMaritalStatusForSpouse(Person spouse) {

        if (spouse == null || !isActiveMember(spouse)) {
            return;
        }

        spouse.setMaritalStatus(MaritalStatusType.MARRIED);
        personRepository.save(spouse);
    }

    private boolean isActiveMember(Person person) {

        return person != null
                && membershipRepository.existsByPersonIdAndCurrentTrueAndStatus(
                        person.getId(),
                        StatusType.ACTIVE
                );
    }

    /**
     * Si feeAmount viene informado (> 0), crea (o actualiza, si ya
     * existía uno enlazado) el FinancialMovement de categoría
     * SERVICE_FEE. El estado (APPROVED/PENDING) sigue el mismo
     * criterio que FinancialMovementServiceImpl.create(): quien crea
     * con autoridad sobre la sede queda aprobado de una vez.
     *
     * Si feeAmount viene null/0 y ya había un movimiento enlazado,
     * se desvincula (no se borra el movimiento — puede que ya haya
     * sido revisado/aprobado, borrarlo sería alterar el historial
     * financiero).
     */
    private void syncFinancialMovement(Marriage marriage, MarriageFormRequest request) {

        BigDecimal fee = request.getFeeAmount();

        if (fee == null || fee.signum() <= 0) {
            marriage.setFinancialMovement(null);
            marriage.setFeeAmount(null);
            return;
        }

        marriage.setFeeAmount(fee);

        Branch branch = marriage.getBranch();

        FinancialMovement movement =
                marriage.getFinancialMovement() != null
                        ? marriage.getFinancialMovement()
                        : new FinancialMovement();

        boolean isNew = movement.getId() == null;

        movement.setOrganization(branch.getOrganization());
        movement.setBranch(branch);
        movement.setCategory(FinancialMovementCategory.SERVICE_FEE);
        movement.setType(FinancialMovementType.INCOME);
        movement.setPaymentMethod(request.getFeePaymentMethod());
        movement.setConcept(
                "Matrimonio: " + marriage.getSpouse1Name() + " & " + marriage.getSpouse2Name()
        );
        movement.setAmount(fee);
        movement.setMovementDate(marriage.getMarriageDate());

        Person memberSpouse =
                isActiveMember(marriage.getSpouse1Person())
                        ? marriage.getSpouse1Person()
                        : (isActiveMember(marriage.getSpouse2Person())
                                ? marriage.getSpouse2Person()
                                : null);

        movement.setPerson(memberSpouse);

        if (isNew) {

            movement.setCreatedByUser(findPersonOrThrow(authContext.getUserId()));

            if (financialAccessGuard.canApprove(branch)) {

                movement.setStatus(FinancialMovementStatus.APPROVED);
                movement.setApprovedByUser(movement.getCreatedByUser());
                movement.setApprovedAt(Instant.now());

            } else {

                movement.setStatus(FinancialMovementStatus.PENDING);
            }
        }

        financialMovementRepository.save(movement);

        marriage.setFinancialMovement(movement);
    }

    /**
     * Igual criterio que FinancialMovementServiceImpl.resolveBranch:
     * solo el org admin elige libremente la sede; cualquier otro rol
     * queda ligado a su propia sede actual.
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
                    "error.debeSeleccionarSedeMatrimonio",
                    HttpStatus.BAD_REQUEST
            );
        }

        return branchRepository.findById(branchId)
                .orElseThrow(() ->
                        new Exceptions("error.sedeNoEncontrada", HttpStatus.NOT_FOUND)
                );
    }

    private void assertSameOrganization(Branch branch) {

        if (!authContext.isSystem()
                && !branch.getOrganization().getId()
                        .equals(authContext.getCurrentOrganizationId())) {

            throw new Exceptions(
                    "error.noTieneAccesoRegistro",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private void validateForm(MarriageFormRequest request) {

        if (request.getMarriageDate() == null) {
            throw new Exceptions(
                    "error.fechaMatrimonioObligatoria",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (request.getChurchName() == null || request.getChurchName().isBlank()) {
            throw new Exceptions(
                    "error.iglesiaDondeCasaronObligatoria",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (request.getSpouse1Name() == null || request.getSpouse1Name().isBlank()) {
            throw new Exceptions(
                    "error.nombrePrimerConyugeObligatorio",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (request.getSpouse2Name() == null || request.getSpouse2Name().isBlank()) {
            throw new Exceptions(
                    "error.nombreSegundoConyugeObligatorio",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private Marriage findOrThrow(UUID id) {

        return marriageRepository.findById(id)
                .orElseThrow(() ->
                        new Exceptions(
                                "error.registroMatrimonioNoEncontrado",
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
