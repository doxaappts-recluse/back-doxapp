package pe.dcs.app.features.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.FinancialMovement;
import pe.dcs.app.entity.InventoryAssignment;
import pe.dcs.app.entity.InventoryItem;
import pe.dcs.app.entity.InventoryMovement;
import pe.dcs.app.entity.Ministry;
import pe.dcs.app.entity.Person;
import pe.dcs.app.features.finance.FinancialAccessGuard;
import pe.dcs.app.features.inventory.mapper.InventoryMapper;
import pe.dcs.app.features.inventory.request.InventoryAssignmentFormRequest;
import pe.dcs.app.features.inventory.request.InventoryAssignmentReturnRequest;
import pe.dcs.app.features.inventory.request.InventoryAssignmentSearchRequest;
import pe.dcs.app.features.inventory.request.InventoryItemFormRequest;
import pe.dcs.app.features.inventory.request.InventoryItemSearchRequest;
import pe.dcs.app.features.inventory.request.InventoryMovementFormRequest;
import pe.dcs.app.features.inventory.request.InventoryMovementSearchRequest;
import pe.dcs.app.features.inventory.response.InventoryAssignmentResponse;
import pe.dcs.app.features.inventory.response.InventoryItemResponse;
import pe.dcs.app.features.inventory.response.InventoryMinistryOptionResponse;
import pe.dcs.app.features.inventory.response.InventoryMovementResponse;
import pe.dcs.app.features.inventory.response.InventoryPersonSearchResponse;
import pe.dcs.app.features.inventory.service.InventoryService;
import pe.dcs.app.repository.BranchRepository;
import pe.dcs.app.repository.FinancialMovementRepository;
import pe.dcs.app.repository.InventoryAssignmentRepository;
import pe.dcs.app.repository.InventoryItemRepository;
import pe.dcs.app.repository.InventoryMovementRepository;
import pe.dcs.app.repository.MembershipRepository;
import pe.dcs.app.repository.MinistryRepository;
import pe.dcs.app.repository.PersonRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.finance.FinancialMovementCategory;
import pe.dcs.app.util.enums.finance.FinancialMovementStatus;
import pe.dcs.app.util.enums.finance.FinancialMovementType;
import pe.dcs.app.util.enums.inventory.InventoryMovementReason;
import pe.dcs.app.util.enums.inventory.InventoryMovementType;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Inventario. Catálogo de ítems (InventoryItem) por sede, no
 * delegable (mismo criterio que ReservableSpace). Movimientos de
 * stock (InventoryMovement, entradas/salidas) y asignaciones de
 * custodia (InventoryAssignment) delegables a la sede. Sin bypass
 * SYSTEM en ningún punto — ver InventoryAccessGuard.
 */
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryItemRepository itemRepository;
    private final InventoryMovementRepository movementRepository;
    private final InventoryAssignmentRepository assignmentRepository;
    private final BranchRepository branchRepository;
    private final PersonRepository personRepository;
    private final MembershipRepository membershipRepository;
    private final MinistryRepository ministryRepository;
    private final FinancialMovementRepository financialMovementRepository;
    private final InventoryMapper mapper;
    private final AuthContext authContext;
    private final InventoryAccessGuard accessGuard;
    private final FinancialAccessGuard financialAccessGuard;

    // =====================================================
    // BUSCAR PERSONA POR DNI (custodio de una asignación)
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public InventoryPersonSearchResponse findPersonByDni(String dni) {

        accessGuard.assertCanUse();

        UUID organizationId = authContext.getCurrentOrganizationId();

        if (organizationId == null) {
            throw new Exceptions("error.noTieneContextoOrganizacionActivo", HttpStatus.FORBIDDEN);
        }

        if (dni == null || dni.isBlank()) {
            throw new Exceptions("error.elDniEsObligatorio", HttpStatus.BAD_REQUEST);
        }

        Person person =
                personRepository.findByDniInOrganization(dni, organizationId)
                        .orElseThrow(() -> new Exceptions(
                                "error.noEncontroNingunaPersonaDniOrganizacion",
                                HttpStatus.NOT_FOUND
                        ));

        boolean isMember =
                membershipRepository.existsByPersonIdAndCurrentTrueAndStatus(person.getId(), StatusType.ACTIVE);

        return new InventoryPersonSearchResponse(
                person.getId(),
                person.getName(),
                person.getLastname(),
                person.getDni(),
                isMember
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryMinistryOptionResponse> listMinistries() {

        accessGuard.assertCanUse();

        return ministryRepository.findAllByStatusOrderByNameEsAsc(StatusType.ACTIVE)
                .stream()
                .map(m -> new InventoryMinistryOptionResponse(m.getId(), m.getLocalizedName()))
                .toList();
    }

    // =====================================================
    // ÍTEMS
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryItemResponse> searchItems(InventoryItemSearchRequest request) {

        accessGuard.assertCanUse();

        Pageable pageable = PageableUtil.buildPageable(request.getPagination(), request.getSorts());

        Page<InventoryItem> page =
                itemRepository.findAll(
                        InventoryItemSpecification.filter(request.getFilters(), authContext),
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(item -> mapper.toItemResponse(
                                item,
                                movementRepository.countByItemId(item.getId()),
                                assignmentRepository.countByItemIdAndReturnedDateIsNull(item.getId()),
                                accessGuard.canManageItem(item),
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
    public InventoryItemResponse getItemById(UUID id) {

        accessGuard.assertCanUse();

        InventoryItem item = findItemOrThrow(id);

        return mapper.toItemResponse(
                item,
                movementRepository.countByItemId(item.getId()),
                assignmentRepository.countByItemIdAndReturnedDateIsNull(item.getId()),
                accessGuard.canManageItem(item),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional
    public UUID createItem(InventoryItemFormRequest request) {

        accessGuard.assertCanCreateItem();

        if (request.getName() == null || request.getName().isBlank()) {
            throw new Exceptions("error.nombreItemObligatorio", HttpStatus.BAD_REQUEST);
        }

        UUID branchId = accessGuard.resolveBranchId(request.getBranchId());

        if (branchId == null) {
            throw new Exceptions("error.debeSeleccionarSedeItem", HttpStatus.BAD_REQUEST);
        }

        InventoryItem item = new InventoryItem();
        item.setName(request.getName());
        item.setDescription(request.getDescription());
        item.setCategory(request.getCategory());
        item.setUnit(hasText(request.getUnit()) ? request.getUnit() : "unidad");
        item.setMinStock(request.getMinStock());
        item.setCurrentQuantity(0);
        item.setBranch(findBranchOrThrow(branchId));
        item.setStatus(request.getStatus() != null ? request.getStatus() : StatusType.ACTIVE);

        itemRepository.save(item);

        return item.getId();
    }

    @Override
    @Transactional
    public void updateItem(UUID id, InventoryItemFormRequest request) {

        InventoryItem item = findItemOrThrow(id);

        accessGuard.assertCanManageItem(item);

        if (request.getName() == null || request.getName().isBlank()) {
            throw new Exceptions("error.nombreItemObligatorio", HttpStatus.BAD_REQUEST);
        }

        item.setName(request.getName());
        item.setDescription(request.getDescription());
        item.setCategory(request.getCategory());
        item.setUnit(hasText(request.getUnit()) ? request.getUnit() : "unidad");
        item.setMinStock(request.getMinStock());

        if (request.getStatus() != null) {
            item.setStatus(request.getStatus());
        }

        // La sede del ítem no se reasigna tras crearlo, ni
        // currentQuantity se edita directamente acá — solo cambia vía
        // InventoryMovement (ver createMovement) — mismo criterio que
        // ReservableSpace.branch / BibleClass.courseId.

        itemRepository.save(item);
    }

    // =====================================================
    // MOVIMIENTOS
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryMovementResponse> searchMovements(InventoryMovementSearchRequest request) {

        accessGuard.assertCanUse();

        Pageable pageable = PageableUtil.buildPageable(request.getPagination(), request.getSorts());

        Page<InventoryMovement> page =
                movementRepository.findAll(
                        InventoryMovementSpecification.filter(request.getFilters(), authContext),
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(m -> mapper.toMovementResponse(m, accessGuard.canManageMovement(m), showAudit))
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
    public InventoryMovementResponse getMovementById(UUID id) {

        accessGuard.assertCanUse();

        InventoryMovement movement = findMovementOrThrow(id);

        return mapper.toMovementResponse(movement, accessGuard.canManageMovement(movement), authContext.canViewAudit());
    }

    /**
     * Los movimientos NO se editan ni eliminan tras crearse — es un
     * libro de movimientos (mismo criterio que FinancialMovement una
     * vez aprobado): si se registró mal, se corrige con un nuevo
     * movimiento de ajuste (reason=ADJUSTMENT) en sentido contrario.
     */
    @Override
    @Transactional
    public UUID createMovement(InventoryMovementFormRequest request) {

        accessGuard.assertCanCreateMovement();

        if (request.getItemId() == null) {
            throw new Exceptions("error.debeSeleccionarItem", HttpStatus.BAD_REQUEST);
        }

        if (request.getType() == null) {
            throw new Exceptions("error.debeIndicarSiEntradaSalida", HttpStatus.BAD_REQUEST);
        }

        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new Exceptions("error.cantidadDebeSerMayorCero", HttpStatus.BAD_REQUEST);
        }

        InventoryItem item = findItemOrThrow(request.getItemId());

        InventoryMovementReason reason =
                request.getReason() != null ? request.getReason() : InventoryMovementReason.OTHER;

        int delta =
                request.getType() == InventoryMovementType.IN
                        ? request.getQuantity()
                        : -request.getQuantity();

        int newQuantity = item.getCurrentQuantity() + delta;

        if (newQuantity < 0) {
            throw new Exceptions(
                    "error.noHayStockSuficienteSalida",
                    HttpStatus.BAD_REQUEST,
                    item.getCurrentQuantity()
            );
        }

        item.setCurrentQuantity(newQuantity);
        itemRepository.save(item);

        InventoryMovement movement = new InventoryMovement();
        movement.setItem(item);
        movement.setType(request.getType());
        movement.setReason(reason);
        movement.setQuantity(request.getQuantity());
        movement.setUnitCost(request.getUnitCost());
        movement.setTotalCost(
                request.getUnitCost() != null
                        ? request.getUnitCost().multiply(BigDecimal.valueOf(request.getQuantity()))
                        : null
        );
        movement.setMovementDate(request.getMovementDate() != null ? request.getMovementDate() : LocalDate.now());
        movement.setNotes(request.getNotes());

        movementRepository.save(movement);

        syncFinancialMovement(movement, item, request);

        return movement.getId();
    }

    /**
     * Solo si type=IN, reason=PURCHASE y unitCost>0: crea el
     * FinancialMovement vinculado (categoría INVENTORY_PURCHASE,
     * tipo EXPENSE) — mismo patrón que
     * MarriageServiceImpl.syncFinancialMovement (categoría
     * SERVICE_FEE), pero acá no hay edición posterior del movimiento
     * de inventario, así que esto solo corre una vez, al crear.
     */
    private void syncFinancialMovement(InventoryMovement movement, InventoryItem item, InventoryMovementFormRequest request) {

        boolean isPurchaseWithCost =
                movement.getType() == InventoryMovementType.IN
                        && movement.getReason() == InventoryMovementReason.PURCHASE
                        && movement.getTotalCost() != null
                        && movement.getTotalCost().signum() > 0;

        if (!isPurchaseWithCost) {
            return;
        }

        Branch branch = item.getBranch();

        FinancialMovement financialMovement = new FinancialMovement();
        financialMovement.setOrganization(branch.getOrganization());
        financialMovement.setBranch(branch);
        financialMovement.setCategory(FinancialMovementCategory.INVENTORY_PURCHASE);
        financialMovement.setType(FinancialMovementType.EXPENSE);
        financialMovement.setPaymentMethod(request.getPaymentMethod());
        financialMovement.setConcept(
                "Compra de inventario: " + item.getName() + " x" + movement.getQuantity()
        );
        financialMovement.setAmount(movement.getTotalCost());
        financialMovement.setMovementDate(movement.getMovementDate());
        financialMovement.setCreatedByUser(findPersonOrThrow(authContext.getUserId()));

        if (financialAccessGuard.canApprove(branch)) {

            financialMovement.setStatus(FinancialMovementStatus.APPROVED);
            financialMovement.setApprovedByUser(financialMovement.getCreatedByUser());
            financialMovement.setApprovedAt(Instant.now());

        } else {

            financialMovement.setStatus(FinancialMovementStatus.PENDING);
        }

        financialMovementRepository.save(financialMovement);

        movement.setFinancialMovement(financialMovement);
        movementRepository.save(movement);
    }

    // =====================================================
    // ASIGNACIONES / CUSTODIA
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryAssignmentResponse> searchAssignments(InventoryAssignmentSearchRequest request) {

        accessGuard.assertCanUse();

        Pageable pageable = PageableUtil.buildPageable(request.getPagination(), request.getSorts());

        Page<InventoryAssignment> page =
                assignmentRepository.findAll(
                        InventoryAssignmentSpecification.filter(request.getFilters(), authContext),
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(a -> mapper.toAssignmentResponse(a, accessGuard.canManageAssignment(a), showAudit))
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
    public InventoryAssignmentResponse getAssignmentById(UUID id) {

        accessGuard.assertCanUse();

        InventoryAssignment assignment = findAssignmentOrThrow(id);

        return mapper.toAssignmentResponse(assignment, accessGuard.canManageAssignment(assignment), authContext.canViewAudit());
    }

    @Override
    @Transactional
    public UUID createAssignment(InventoryAssignmentFormRequest request) {

        accessGuard.assertCanCreateAssignment();

        if (request.getItemId() == null) {
            throw new Exceptions("error.debeSeleccionarItem", HttpStatus.BAD_REQUEST);
        }

        boolean hasPerson = request.getAssignedToPersonId() != null;
        boolean hasMinistry = request.getAssignedToMinistryId() != null;

        if (hasPerson == hasMinistry) {
            throw new Exceptions(
                    "error.debeAsignarItemPersonaMinisterioUno",
                    HttpStatus.BAD_REQUEST
            );
        }

        InventoryItem item = findItemOrThrow(request.getItemId());

        InventoryAssignment assignment = new InventoryAssignment();
        assignment.setItem(item);
        assignment.setQuantity(request.getQuantity() != null && request.getQuantity() > 0 ? request.getQuantity() : 1);
        assignment.setAssignedToPerson(hasPerson ? findPersonOrThrow(request.getAssignedToPersonId()) : null);
        assignment.setAssignedToMinistry(hasMinistry ? findMinistryOrThrow(request.getAssignedToMinistryId()) : null);
        assignment.setAssignedDate(request.getAssignedDate() != null ? request.getAssignedDate() : LocalDate.now());
        assignment.setExpectedReturnDate(request.getExpectedReturnDate());
        assignment.setNotes(request.getNotes());

        assignmentRepository.save(assignment);

        return assignment.getId();
    }

    @Override
    @Transactional
    public void returnAssignment(UUID id, InventoryAssignmentReturnRequest request) {

        InventoryAssignment assignment = findAssignmentOrThrow(id);

        accessGuard.assertCanManageAssignment(assignment);

        if (assignment.getReturnedDate() != null) {
            throw new Exceptions("error.itemFueDevuelto", HttpStatus.BAD_REQUEST);
        }

        assignment.setReturnedDate(
                request != null && request.getReturnedDate() != null ? request.getReturnedDate() : LocalDate.now()
        );

        if (request != null && request.getNotes() != null && !request.getNotes().isBlank()) {
            assignment.setNotes(
                    hasText(assignment.getNotes())
                            ? assignment.getNotes() + " / Devolución: " + request.getNotes()
                            : request.getNotes()
            );
        }

        assignmentRepository.save(assignment);
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private InventoryItem findItemOrThrow(UUID id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new Exceptions("error.itemNoEncontrado", HttpStatus.NOT_FOUND));
    }

    private InventoryMovement findMovementOrThrow(UUID id) {
        return movementRepository.findById(id)
                .orElseThrow(() -> new Exceptions("error.movimientoNoEncontrado", HttpStatus.NOT_FOUND));
    }

    private InventoryAssignment findAssignmentOrThrow(UUID id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new Exceptions("error.asignacionNoEncontrada", HttpStatus.NOT_FOUND));
    }

    private Branch findBranchOrThrow(UUID id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new Exceptions("error.sedeNoEncontrada2", HttpStatus.NOT_FOUND));
    }

    private Person findPersonOrThrow(UUID id) {
        return personRepository.findById(id)
                .orElseThrow(() -> new Exceptions("error.personaNoEncontrada", HttpStatus.NOT_FOUND));
    }

    private Ministry findMinistryOrThrow(UUID id) {
        return ministryRepository.findById(id)
                .orElseThrow(() -> new Exceptions("error.ministerioNoEncontrado2", HttpStatus.NOT_FOUND));
    }
}
