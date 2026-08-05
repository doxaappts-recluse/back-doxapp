package pe.dcs.app.features.finance;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.FinancialFund;
import pe.dcs.app.entity.FinancialMovement;
import pe.dcs.app.entity.Person;
import pe.dcs.app.features.finance.request.FinancialMovementApproveRequest;
import pe.dcs.app.features.finance.request.FinancialMovementFilter;
import pe.dcs.app.features.finance.request.FinancialMovementRejectRequest;
import pe.dcs.app.features.finance.request.FinancialMovementRequest;
import pe.dcs.app.features.finance.request.FinancialMovementSearchRequest;
import pe.dcs.app.features.finance.response.FinancialDonorResponse;
import pe.dcs.app.features.finance.response.FinancialMovementResponse;
import pe.dcs.app.features.finance.response.FinancialMovementSummaryResponse;
import pe.dcs.app.repository.BranchRepository;
import pe.dcs.app.repository.FinancialFundRepository;
import pe.dcs.app.repository.FinancialMovementRepository;
import pe.dcs.app.repository.MembershipRepository;
import pe.dcs.app.repository.PersonRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.finance.FinancialMovementCategory;
import pe.dcs.app.util.enums.finance.FinancialMovementStatus;
import pe.dcs.app.util.enums.finance.FinancialMovementType;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinancialMovementServiceImpl
        implements FinancialMovementService {

    private final FinancialMovementRepository financialMovementRepository;
    private final BranchRepository branchRepository;
    private final PersonRepository personRepository;
    private final FinancialFundRepository financialFundRepository;
    private final MembershipRepository membershipRepository;
    private final FinancialMovementMapper financialMovementMapper;
    private final FinancialAccessGuard financialAccessGuard;
    private final AuthContext authContext;

    private FinancialMovementResponse toResponse(
            FinancialMovement movement,
            boolean showAudit
    ) {

        FinancialMovementResponse response =
                financialMovementMapper.simple(movement, showAudit);

        response.setCanManage(
                financialAccessGuard.canManage(movement)
        );

        UUID currentUserId = authContext.getUserId();

        response.setOwner(
                movement.getCreatedByUser() != null
                        && currentUserId != null
                        && movement.getCreatedByUser().getId().equals(currentUserId)
        );

        return response;
    }

    private Branch findBranch(UUID branchId) {

        return branchRepository.findById(branchId)
                .orElseThrow(() ->
                        new Exceptions(
                                "error.sedeNoEncontrada",
                                HttpStatus.NOT_FOUND
                        )
                );
    }

    private Person findUser(UUID userId) {

        return personRepository.findById(userId)
                .orElseThrow(() ->
                        new Exceptions(
                                "error.usuarioNoEncontrado",
                                HttpStatus.NOT_FOUND
                        )
                );
    }

    /**
     * Resuelve el fondo del movimiento validando que pertenezca a
     * la misma organización de la sede del movimiento (un fondo de
     * otra organización jamás debe poder asignarse).
     */
    private FinancialFund findFund(UUID fundId, Branch branch) {

        FinancialFund fund = financialFundRepository.findById(fundId)
                .orElseThrow(() ->
                        new Exceptions(
                                "error.fondoNoEncontrado",
                                HttpStatus.NOT_FOUND
                        )
                );

        if (!fund.getOrganization().getId()
                .equals(branch.getOrganization().getId())) {

            throw new Exceptions(
                    "error.fondoNoPerteneceOrganizacion",
                    HttpStatus.BAD_REQUEST
            );
        }

        return fund;
    }

    /**
     * TITHE/OFFERING/DONATION/OTHER_INCOME son INCOME, EXPENSE es
     * EXPENSE — el tipo se deriva de la categoría en vez de
     * confiar en lo que mande el front, para que nunca queden
     * inconsistentes.
     */
    private FinancialMovementType deriveType(FinancialMovementCategory category) {

        return category == FinancialMovementCategory.EXPENSE
                || category == FinancialMovementCategory.INVENTORY_PURCHASE
                || category == FinancialMovementCategory.PAYROLL
                ? FinancialMovementType.EXPENSE
                : FinancialMovementType.INCOME;
    }

    /**
     * Cualquiera que no sea org admin (branch admin u org user
     * delegado) queda ligado a su propia sede automáticamente, sin
     * poder elegir otra — solo el org admin elige libremente la
     * sede del movimiento (mismo criterio que
     * EventServiceImpl.applyScopeAndBranch).
     */
    private Branch resolveBranch(FinancialMovementRequest request) {

        if (!authContext.isCurrentOrganizationAdmin()) {

            return branchRepository.findById(
                    authContext.getCurrentBranchId()
            ).orElseThrow(() ->
                    new Exceptions(
                            "error.sedeNoEncontrada",
                            HttpStatus.NOT_FOUND
                    )
            );
        }

        if (request.getBranchId() == null) {
            throw new Exceptions(
                    "error.debeSeleccionarSedeMovimiento",
                    HttpStatus.BAD_REQUEST
            );
        }

        return findBranch(request.getBranchId());
    }

    @Override
    @Transactional
    public FinancialMovementResponse create(
            FinancialMovementRequest request
    ) {

        Branch branch = resolveBranch(request);

        financialAccessGuard.assertSameOrganization(branch);
        financialAccessGuard.assertCanCreate(branch);

        Person user = findUser(authContext.getUserId());

        FinancialMovement movement = new FinancialMovement();

        movement.setOrganization(branch.getOrganization());
        movement.setBranch(branch);
        movement.setCategory(request.getCategory());
        movement.setType(deriveType(request.getCategory()));
        movement.setPaymentMethod(request.getPaymentMethod());
        movement.setConcept(request.getConcept());
        movement.setAmount(request.getAmount());
        movement.setMovementDate(request.getMovementDate());
        movement.setObservations(request.getObservations());
        movement.setCreatedByUser(user);

        if (request.getPersonId() != null) {
            movement.setPerson(findUser(request.getPersonId()));
        }

        if (request.getFundId() != null) {
            movement.setFund(findFund(request.getFundId(), branch));
        }

        if (financialAccessGuard.canApprove(branch)) {

            movement.setStatus(FinancialMovementStatus.APPROVED);
            movement.setApprovedByUser(user);
            movement.setApprovedAt(Instant.now());

        } else {

            movement.setStatus(FinancialMovementStatus.PENDING);
        }

        return toResponse(
                financialMovementRepository.save(movement),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional
    public FinancialMovementResponse update(
            UUID id,
            FinancialMovementRequest request
    ) {

        FinancialMovement movement =
                financialMovementRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.movimientoFinancieroNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (movement.getStatus() == FinancialMovementStatus.APPROVED) {

            throw new Exceptions(
                    "error.noPuedeEditarMovimientoAprobado",
                    HttpStatus.BAD_REQUEST
            );
        }

        financialAccessGuard.assertCanManage(movement);

        movement.setCategory(request.getCategory());
        movement.setType(deriveType(request.getCategory()));
        movement.setPaymentMethod(request.getPaymentMethod());
        movement.setConcept(request.getConcept());
        movement.setAmount(request.getAmount());
        movement.setMovementDate(request.getMovementDate());
        movement.setObservations(request.getObservations());

        movement.setPerson(
                request.getPersonId() != null
                        ? findUser(request.getPersonId())
                        : null
        );

        movement.setFund(
                request.getFundId() != null
                        ? findFund(request.getFundId(), movement.getBranch())
                        : null
        );

        if (movement.getStatus() == FinancialMovementStatus.REJECTED) {

            movement.setStatus(FinancialMovementStatus.PENDING);
            movement.setApprovedByUser(null);
            movement.setApprovedAt(null);
        }

        movement.setUpdatedAt(Instant.now());

        return toResponse(
                financialMovementRepository.save(movement),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional
    public FinancialMovementResponse approve(
            UUID id,
            FinancialMovementApproveRequest request
    ) {

        FinancialMovement movement =
                financialMovementRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.movimientoFinancieroNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (!financialAccessGuard.canApprove(movement.getBranch())) {

            throw new Exceptions(
                    "error.noTienePermisosAprobarMovimientosSede",
                    HttpStatus.FORBIDDEN
            );
        }

        if (movement.getStatus() != FinancialMovementStatus.PENDING) {

            throw new Exceptions(
                    "error.soloPuedenAprobarMovimientosPendientes",
                    HttpStatus.BAD_REQUEST
            );
        }

        Person admin = findUser(authContext.getUserId());

        movement.setStatus(FinancialMovementStatus.APPROVED);
        movement.setApprovedByUser(admin);
        movement.setApprovedAt(Instant.now());

        if (request != null && request.getObservations() != null) {
            movement.setObservations(request.getObservations());
        }

        return toResponse(
                financialMovementRepository.save(movement),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional
    public FinancialMovementResponse reject(
            UUID id,
            FinancialMovementRejectRequest request
    ) {

        FinancialMovement movement =
                financialMovementRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.movimientoFinancieroNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (!financialAccessGuard.canApprove(movement.getBranch())) {

            throw new Exceptions(
                    "error.noTienePermisosRechazarMovimientosSede",
                    HttpStatus.FORBIDDEN
            );
        }

        if (movement.getStatus() != FinancialMovementStatus.PENDING) {

            throw new Exceptions(
                    "error.soloPuedenRechazarMovimientosPendientes",
                    HttpStatus.BAD_REQUEST
            );
        }

        movement.setStatus(FinancialMovementStatus.REJECTED);
        movement.setObservations(request.getReason());
        movement.setUpdatedAt(Instant.now());

        return toResponse(
                financialMovementRepository.save(movement),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialMovementResponse getById(UUID id) {

        FinancialMovement movement =
                financialMovementRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.movimientoFinancieroNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        financialAccessGuard.assertSameOrganization(movement.getBranch());

        return toResponse(
                movement,
                authContext.canViewAudit()
        );
    }

    @Override
    public PageResponse<FinancialMovementResponse> search(
            FinancialMovementSearchRequest request
    ) {

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        FinancialMovementFilter filters =
                request.getFilters() != null
                        ? request.getFilters()
                        : new FinancialMovementFilter();

        Specification<FinancialMovement> spec =
                FinancialMovementSpecification.filter(
                        authContext,
                        filters.getBranchId(),
                        filters.getType(),
                        filters.getCategory(),
                        filters.getStatus(),
                        filters.getPersonId(),
                        filters.getFundId(),
                        filters.getStartDate(),
                        filters.getEndDate(),
                        filters.getOnlyAnonymous()
                );

        Page<FinancialMovement> page =
                financialMovementRepository.findAll(spec, pageable);

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(m -> toResponse(m, showAudit))
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
    public FinancialMovementSummaryResponse summary(
            FinancialMovementFilter filters
    ) {

        FinancialMovementFilter safeFilters =
                filters != null
                        ? filters
                        : new FinancialMovementFilter();

        /*
         * Saldo/total siempre sobre movimientos APROBADOS, sin
         * importar lo que traiga safeFilters.getStatus() — un
         * PENDING no es plata real todavía y un REJECTED nunca
         * ocurrió, así que ninguno de los dos debe alterar un saldo.
         */
        Specification<FinancialMovement> spec =
                FinancialMovementSpecification.filter(
                        authContext,
                        safeFilters.getBranchId(),
                        safeFilters.getType(),
                        safeFilters.getCategory(),
                        FinancialMovementStatus.APPROVED,
                        safeFilters.getPersonId(),
                        safeFilters.getFundId(),
                        safeFilters.getStartDate(),
                        safeFilters.getEndDate(),
                        safeFilters.getOnlyAnonymous()
                );

        List<FinancialMovement> movements =
                financialMovementRepository.findAll(spec);

        BigDecimal totalIncome =
                movements.stream()
                        .filter(m -> m.getType() == FinancialMovementType.INCOME)
                        .map(FinancialMovement::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalExpense =
                movements.stream()
                        .filter(m -> m.getType() == FinancialMovementType.EXPENSE)
                        .map(FinancialMovement::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        FinancialMovementSummaryResponse response =
                new FinancialMovementSummaryResponse();

        response.setTotalIncome(totalIncome);
        response.setTotalExpense(totalExpense);
        response.setBalance(totalIncome.subtract(totalExpense));
        response.setMovementCount(movements.size());

        return response;
    }

    @Override
    public List<FinancialDonorResponse> donors(
            FinancialMovementFilter filters
    ) {

        FinancialMovementFilter safeFilters =
                filters != null
                        ? filters
                        : new FinancialMovementFilter();

        /*
         * Donantes = quiénes aportaron dinero, así que se restringe
         * siempre a INCOME/APROBADO sin importar lo que traiga
         * safeFilters (mismo criterio que summary() con status).
         * Los EXPENSE no tienen "donante".
         */
        Specification<FinancialMovement> spec =
                FinancialMovementSpecification.filter(
                        authContext,
                        safeFilters.getBranchId(),
                        FinancialMovementType.INCOME,
                        safeFilters.getCategory(),
                        FinancialMovementStatus.APPROVED,
                        safeFilters.getPersonId(),
                        safeFilters.getFundId(),
                        safeFilters.getStartDate(),
                        safeFilters.getEndDate(),
                        safeFilters.getOnlyAnonymous()
                );

        List<FinancialMovement> movements =
                financialMovementRepository.findAll(spec);

        /*
         * Se agrupa a mano (no con Collectors.groupingBy) porque el
         * "donante anónimo" agrupa bajo personId=null, y
         * groupingBy() lanza NPE si el classifier devuelve null.
         * HashMap sí admite null como key.
         */
        Map<UUID, BigDecimal> totalsByPerson = new HashMap<>();
        Map<UUID, Long> countsByPerson = new HashMap<>();
        Map<UUID, Person> personById = new HashMap<>();

        for (FinancialMovement movement : movements) {

            UUID personId =
                    movement.getPerson() != null
                            ? movement.getPerson().getId()
                            : null;

            totalsByPerson.merge(personId, movement.getAmount(), BigDecimal::add);
            countsByPerson.merge(personId, 1L, Long::sum);

            if (movement.getPerson() != null) {
                personById.putIfAbsent(personId, movement.getPerson());
            }
        }

        List<FinancialDonorResponse> result = new ArrayList<>();

        for (Map.Entry<UUID, BigDecimal> entry : totalsByPerson.entrySet()) {

            UUID personId = entry.getKey();

            FinancialDonorResponse response = new FinancialDonorResponse();

            if (personId == null) {

                response.setAnonymous(true);

            } else {

                Person person = personById.get(personId);

                response.setPersonId(personId);
                response.setPersonName(person.getName());
                response.setPersonLastname(person.getLastname());
                response.setDni(person.getDni());

                response.setMember(
                        membershipRepository.existsByPersonIdAndCurrentTrueAndStatus(
                                personId,
                                StatusType.ACTIVE
                        )
                );
            }

            response.setTotalIncome(entry.getValue());
            response.setMovementCount(countsByPerson.get(personId));

            result.add(response);
        }

        /*
         * Anónimo siempre al final; el resto por apellido/nombre,
         * consistente con cómo se listan personas en el resto del
         * sistema (ver p.ej. EventPersonSpecification).
         */
        result.sort(
                Comparator
                        .comparing(FinancialDonorResponse::isAnonymous)
                        .thenComparing(
                                d -> d.getPersonLastname() != null
                                        ? d.getPersonLastname()
                                        : "",
                                String.CASE_INSENSITIVE_ORDER
                        )
                        .thenComparing(
                                d -> d.getPersonName() != null
                                        ? d.getPersonName()
                                        : "",
                                String.CASE_INSENSITIVE_ORDER
                        )
        );

        return result;
    }
}
