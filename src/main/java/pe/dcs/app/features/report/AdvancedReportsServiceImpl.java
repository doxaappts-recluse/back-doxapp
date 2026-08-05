package pe.dcs.app.features.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.Event;
import pe.dcs.app.features.baptism.BaptismSpecification;
import pe.dcs.app.features.baptism.request.BaptismFilterRequest;
import pe.dcs.app.features.baptism.request.BaptismSearchRequest;
import pe.dcs.app.features.baptism.response.BaptismSearchRowResponse;
import pe.dcs.app.features.baptism.service.BaptismService;
import pe.dcs.app.features.contract.service.ContractService;
import pe.dcs.app.features.event.mapper.EventMapper;
import pe.dcs.app.features.event.request.event.EventFilter;
import pe.dcs.app.features.event.response.event.EventResponse;
import pe.dcs.app.features.event.specification.EventSpecification;
import pe.dcs.app.features.finance.FinancialBudgetService;
import pe.dcs.app.features.finance.FinancialMovementService;
import pe.dcs.app.features.finance.request.FinancialMovementFilter;
import pe.dcs.app.features.finance.request.FinancialMovementSearchRequest;
import pe.dcs.app.features.finance.response.FinancialMovementResponse;
import pe.dcs.app.features.finance.response.FinancialMovementSummaryResponse;
import pe.dcs.app.features.report.request.ReportFilterRequest;
import pe.dcs.app.features.hr.LeaveRequestSpecification;
import pe.dcs.app.features.hr.StaffMemberSpecification;
import pe.dcs.app.features.hr.request.LeaveRequestFilterRequest;
import pe.dcs.app.features.hr.request.LeaveRequestSearchRequest;
import pe.dcs.app.features.hr.request.StaffMemberFilterRequest;
import pe.dcs.app.features.hr.response.LeaveRequestResponse;
import pe.dcs.app.features.hr.service.HrService;
import pe.dcs.app.features.inventory.InventoryAssignmentSpecification;
import pe.dcs.app.features.inventory.InventoryItemSpecification;
import pe.dcs.app.features.inventory.request.InventoryAssignmentFilterRequest;
import pe.dcs.app.features.inventory.request.InventoryItemFilterRequest;
import pe.dcs.app.features.inventory.request.InventoryMovementFilterRequest;
import pe.dcs.app.features.inventory.request.InventoryMovementSearchRequest;
import pe.dcs.app.features.inventory.response.InventoryMovementResponse;
import pe.dcs.app.features.inventory.service.InventoryService;
import pe.dcs.app.features.marriage.MarriageSpecification;
import pe.dcs.app.features.marriage.request.MarriageFilterRequest;
import pe.dcs.app.features.marriage.request.MarriageSearchRequest;
import pe.dcs.app.features.marriage.response.MarriageSearchRowResponse;
import pe.dcs.app.features.marriage.service.MarriageService;
import pe.dcs.app.features.membership.MembershipSpecification;
import pe.dcs.app.features.membership.request.MembershipFilterRequest;
import pe.dcs.app.features.membership.request.MembershipSearchRequest;
import pe.dcs.app.features.membership.response.MembershipSearchRowResponse;
import pe.dcs.app.features.membership.service.MembershipService;
import pe.dcs.app.features.pastoral_followup.InactiveMemberSpecification;
import pe.dcs.app.features.pastoral_followup.request.InactiveMemberFilterRequest;
import pe.dcs.app.features.pastoral_followup.request.InactiveMemberSearchRequest;
import pe.dcs.app.features.pastoral_followup.response.InactiveMemberResponse;
import pe.dcs.app.features.pastoral_followup.service.PastoralFollowUpService;
import pe.dcs.app.features.report.response.BaptismCard;
import pe.dcs.app.features.report.response.BibleAcademyCard;
import pe.dcs.app.features.report.response.EventsCard;
import pe.dcs.app.features.report.response.ExecutiveDashboardResponse;
import pe.dcs.app.features.report.response.HrCard;
import pe.dcs.app.features.report.response.InventoryCard;
import pe.dcs.app.features.report.response.MarriageCard;
import pe.dcs.app.features.report.response.MembershipCard;
import pe.dcs.app.features.report.response.PastoralFollowUpCard;
import pe.dcs.app.features.report.response.SmallGroupCard;
import pe.dcs.app.features.report.response.SpaceReservationCard;
import pe.dcs.app.features.report.response.VisitorCard;
import pe.dcs.app.features.smallgroup.SmallGroupSpecification;
import pe.dcs.app.features.smallgroup.request.SmallGroupFilterRequest;
import pe.dcs.app.features.smallgroup.request.SmallGroupSearchRequest;
import pe.dcs.app.features.smallgroup.response.SmallGroupSearchRowResponse;
import pe.dcs.app.features.smallgroup.service.SmallGroupService;
import pe.dcs.app.features.space_reservation.ReservableSpaceSpecification;
import pe.dcs.app.features.space_reservation.SpaceReservationSpecification;
import pe.dcs.app.features.space_reservation.request.ReservableSpaceFilterRequest;
import pe.dcs.app.features.space_reservation.request.SpaceReservationFilterRequest;
import pe.dcs.app.features.space_reservation.request.SpaceReservationSearchRequest;
import pe.dcs.app.features.space_reservation.response.SpaceReservationResponse;
import pe.dcs.app.features.space_reservation.service.SpaceReservationService;
import pe.dcs.app.features.visitor.VisitorFilterRequest;
import pe.dcs.app.features.visitor.VisitorSpecification;
import pe.dcs.app.features.visitor.request.VisitorSearchRequest;
import pe.dcs.app.features.visitor.response.VisitorSearchRowResponse;
import pe.dcs.app.features.visitor.service.VisitorService;
import pe.dcs.app.repository.BibleEnrollmentRepository;
import pe.dcs.app.repository.BranchRepository;
import pe.dcs.app.repository.EventRepository;
import pe.dcs.app.repository.InventoryAssignmentRepository;
import pe.dcs.app.repository.InventoryItemRepository;
import pe.dcs.app.repository.LeaveRequestRepository;
import pe.dcs.app.repository.MarriageRepository;
import pe.dcs.app.repository.PersonRepository;
import pe.dcs.app.repository.ReservableSpaceRepository;
import pe.dcs.app.repository.SmallGroupRepository;
import pe.dcs.app.repository.SpaceReservationRepository;
import pe.dcs.app.repository.StaffMemberRepository;
import pe.dcs.app.repository.VisitorRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import org.springframework.http.HttpStatus;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.bible_academy.BibleEnrollmentStatus;
import pe.dcs.app.util.enums.hr.HrApprovalStatus;
import pe.dcs.app.util.enums.visitor.VisitorConsolidationStage;
import pe.dcs.app.util.pagination.PaginationRequest;

import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dashboard Ejecutivo (Reportes Avanzados). Reutiliza al máximo las
 * Specification/Service ya existentes de cada módulo — org/branch
 * admin (nunca SYSTEM, ver AdvancedReportsAccessGuard) recibe el
 * conteo correspondiente solo si el módulo está activo en su
 * contrato actual (ver ContractService.getActiveModuleCodesForCurrentContext()).
 * No se persiste nada acá: todo se calcula on-demand, igual criterio
 * que FinancialBudgetServiceImpl.progress().
 */
@Service
@RequiredArgsConstructor
public class AdvancedReportsServiceImpl implements AdvancedReportsService {

    private final AdvancedReportsAccessGuard accessGuard;
    private final AuthContext authContext;
    private final ContractService contractService;
    private final BranchRepository branchRepository;

    private final PersonRepository personRepository;
    private final MarriageRepository marriageRepository;
    private final VisitorRepository visitorRepository;
    private final SmallGroupRepository smallGroupRepository;
    private final BibleEnrollmentRepository bibleEnrollmentRepository;
    private final ReservableSpaceRepository reservableSpaceRepository;
    private final SpaceReservationRepository spaceReservationRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryAssignmentRepository inventoryAssignmentRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EventRepository eventRepository;

    private final FinancialMovementService financialMovementService;
    private final FinancialBudgetService financialBudgetService;

    private final EventMapper eventMapper;

    /*
     * Servicios propios de cada módulo, reutilizados tal cual para
     * las tablas de detalle de Reportes Avanzados (mismo criterio
     * que financialMovementService/eventMapper arriba): cada uno ya
     * resuelve su propio scoping org/sede vía AuthContext dentro de
     * su Specification, así que no hay lógica de negocio nueva acá,
     * solo mapear ReportFilterRequest a la request nativa de cada
     * módulo.
     */
    private final MembershipService membershipService;
    private final BaptismService baptismService;
    private final MarriageService marriageService;
    private final VisitorService visitorService;
    private final PastoralFollowUpService pastoralFollowUpService;
    private final SmallGroupService smallGroupService;
    private final SpaceReservationService spaceReservationService;
    private final InventoryService inventoryService;
    private final HrService hrService;

    @Override
    @Transactional(readOnly = true)
    public ExecutiveDashboardResponse getExecutiveDashboard() {

        accessGuard.assertCanUse();

        boolean isOrgAdmin = authContext.isCurrentOrganizationAdmin();
        UUID organizationId = authContext.getCurrentOrganizationId();
        UUID branchId = authContext.getCurrentBranchId();

        Set<String> activeModules =
                Set.copyOf(contractService.getActiveModuleCodesForCurrentContext());

        ExecutiveDashboardResponse.ExecutiveDashboardResponseBuilder builder =
                ExecutiveDashboardResponse.builder()
                        .scope(isOrgAdmin ? "ORGANIZATION" : "BRANCH")
                        .branchId(branchId)
                        .generatedAt(LocalDateTime.now());

        if (!isOrgAdmin && branchId != null) {
            branchRepository.findById(branchId)
                    .map(Branch::getName)
                    .ifPresent(builder::branchName);
        }

        if (activeModules.contains("MEMBERSHIP")) {
            builder.membership(buildMembershipCard());
        }

        if (activeModules.contains("BAPTISM")) {
            builder.baptism(buildBaptismCard());
        }

        if (activeModules.contains("MARRIAGE")) {
            builder.marriage(buildMarriageCard());
        }

        if (activeModules.contains("VISITOR")) {
            builder.visitor(buildVisitorCard());
        }

        if (activeModules.contains("PASTORAL_FOLLOWUP")) {
            builder.pastoralFollowUp(buildPastoralFollowUpCard());
        }

        if (activeModules.contains("SMALL_GROUP")) {
            builder.smallGroup(buildSmallGroupCard());
        }

        if (activeModules.contains("BIBLE_ACADEMY")) {
            builder.bibleAcademy(buildBibleAcademyCard(isOrgAdmin, organizationId, branchId));
        }

        if (activeModules.contains("SPACE_RESERVATION")) {
            builder.spaceReservation(buildSpaceReservationCard());
        }

        if (activeModules.contains("INVENTORY")) {
            builder.inventory(buildInventoryCard());
        }

        /*
         * "HR" ya no es asignable a contratos desde la reestructuración
         * de RRHH (pasó a ser el módulo padre, ver import.sql) — los
         * hijos delegables son STAFF_MEMBER/LEAVE_REQUEST/PAYROLL. Se
         * chequea cualquiera de los dos que alimentan esta card
         * (fichas activas y solicitudes pendientes).
         */
        if (activeModules.contains("STAFF_MEMBER") || activeModules.contains("LEAVE_REQUEST")) {
            builder.hr(buildHrCard());
        }

        if (activeModules.contains("EVENTS")) {
            builder.events(buildEventsCard(isOrgAdmin, organizationId, branchId));
        }

        if (activeModules.contains("FINANCIAL_MOVEMENT")) {
            builder.finance(buildFinanceCard());
        }

        /*
         * Presupuestos es exclusivo de org admin (ver
         * FinancialBudgetServiceImpl.assertCanManage) aunque el
         * contrato lo tenga activo también para una sede puntual —
         * un branch admin nunca puede llamar progressForPeriod().
         */
        if (isOrgAdmin && activeModules.contains("FINANCIAL_BUDGET")) {
            LocalDate today = LocalDate.now();
            builder.budgets(
                    financialBudgetService.progressForPeriod(
                            today.getYear(),
                            today.getMonthValue()
                    )
            );
        }

        return builder.build();
    }

    private MembershipCard buildMembershipCard() {

        MembershipFilterRequest filter = new MembershipFilterRequest();
        filter.setMembershipStatus(StatusType.ACTIVE);

        MembershipSearchRequest request = new MembershipSearchRequest();
        request.setFilters(filter);

        long activeMembers =
                personRepository.count(
                        MembershipSpecification.filter(request, authContext)
                );

        return MembershipCard.builder()
                .activeMembers(activeMembers)
                .build();
    }

    private BaptismCard buildBaptismCard() {

        BaptismFilterRequest filter = new BaptismFilterRequest();
        filter.setHasBaptism(true);

        BaptismSearchRequest request = new BaptismSearchRequest();
        request.setFilters(filter);

        long totalBaptized =
                personRepository.count(
                        BaptismSpecification.filter(request, authContext)
                );

        return BaptismCard.builder()
                .totalBaptized(totalBaptized)
                .build();
    }

    private MarriageCard buildMarriageCard() {

        long totalMarriages =
                marriageRepository.count(
                        MarriageSpecification.filter(new MarriageFilterRequest(), authContext)
                );

        return MarriageCard.builder()
                .totalMarriages(totalMarriages)
                .build();
    }

    private VisitorCard buildVisitorCard() {

        long total =
                visitorRepository.count(
                        VisitorSpecification.filter(new VisitorFilterRequest(), authContext)
                );

        long converted = countVisitorsByStage(VisitorConsolidationStage.CONVERTED);
        long inConsolidation =
                countVisitorsByStage(VisitorConsolidationStage.NEW)
                        + countVisitorsByStage(VisitorConsolidationStage.IN_FOLLOWUP)
                        + countVisitorsByStage(VisitorConsolidationStage.INTEGRATED);

        return VisitorCard.builder()
                .totalVisitors(total)
                .converted(converted)
                .inConsolidation(inConsolidation)
                .build();
    }

    private long countVisitorsByStage(VisitorConsolidationStage stage) {

        VisitorFilterRequest filter = new VisitorFilterRequest();
        filter.setConsolidationStage(stage);

        return visitorRepository.count(
                VisitorSpecification.filter(filter, authContext)
        );
    }

    private PastoralFollowUpCard buildPastoralFollowUpCard() {

        long inactiveMembers =
                personRepository.count(
                        InactiveMemberSpecification.filter(
                                new InactiveMemberFilterRequest(),
                                authContext
                        )
                );

        return PastoralFollowUpCard.builder()
                .inactiveMembers(inactiveMembers)
                .build();
    }

    private SmallGroupCard buildSmallGroupCard() {

        SmallGroupFilterRequest filter = new SmallGroupFilterRequest();
        filter.setStatus(StatusType.ACTIVE);

        long activeGroups =
                smallGroupRepository.count(
                        SmallGroupSpecification.filter(filter, authContext)
                );

        return SmallGroupCard.builder()
                .activeGroups(activeGroups)
                .build();
    }

    private BibleAcademyCard buildBibleAcademyCard(
            boolean isOrgAdmin,
            UUID organizationId,
            UUID branchId
    ) {

        long activeEnrollments =
                isOrgAdmin
                        ? bibleEnrollmentRepository
                                .countByStatusAndBibleClass_Branch_Organization_Id(
                                        BibleEnrollmentStatus.APPROVED,
                                        organizationId
                                )
                        : bibleEnrollmentRepository
                                .countByStatusAndBibleClass_Branch_Id(
                                        BibleEnrollmentStatus.APPROVED,
                                        branchId
                                );

        return BibleAcademyCard.builder()
                .activeEnrollments(activeEnrollments)
                .build();
    }

    private SpaceReservationCard buildSpaceReservationCard() {

        ReservableSpaceFilterRequest spaceFilter = new ReservableSpaceFilterRequest();
        spaceFilter.setStatus(StatusType.ACTIVE);

        long activeSpaces =
                reservableSpaceRepository.count(
                        ReservableSpaceSpecification.filter(spaceFilter, authContext)
                );

        SpaceReservationFilterRequest reservationFilter = new SpaceReservationFilterRequest();
        reservationFilter.setFromDateTime(LocalDateTime.now());

        long upcomingReservations =
                spaceReservationRepository.count(
                        SpaceReservationSpecification.filter(reservationFilter, authContext)
                );

        return SpaceReservationCard.builder()
                .activeSpaces(activeSpaces)
                .upcomingReservations(upcomingReservations)
                .build();
    }

    private InventoryCard buildInventoryCard() {

        InventoryItemFilterRequest itemFilter = new InventoryItemFilterRequest();
        itemFilter.setStatus(StatusType.ACTIVE);
        itemFilter.setLowStockOnly(true);

        long lowStockItems =
                inventoryItemRepository.count(
                        InventoryItemSpecification.filter(itemFilter, authContext)
                );

        InventoryAssignmentFilterRequest assignmentFilter = new InventoryAssignmentFilterRequest();
        assignmentFilter.setActiveOnly(true);

        long activeAssignments =
                inventoryAssignmentRepository.count(
                        InventoryAssignmentSpecification.filter(assignmentFilter, authContext)
                );

        return InventoryCard.builder()
                .lowStockItems(lowStockItems)
                .activeAssignments(activeAssignments)
                .build();
    }

    private HrCard buildHrCard() {

        StaffMemberFilterRequest staffFilter = new StaffMemberFilterRequest();
        staffFilter.setStatus(StatusType.ACTIVE);

        long activeStaff =
                staffMemberRepository.count(
                        StaffMemberSpecification.filter(staffFilter, authContext)
                );

        LeaveRequestFilterRequest leaveFilter = new LeaveRequestFilterRequest();
        leaveFilter.setStatus(HrApprovalStatus.PENDING);

        long pendingLeaveRequests =
                leaveRequestRepository.count(
                        LeaveRequestSpecification.filter(leaveFilter, authContext)
                );

        return HrCard.builder()
                .activeStaff(activeStaff)
                .pendingLeaveRequests(pendingLeaveRequests)
                .build();
    }

    private EventsCard buildEventsCard(
            boolean isOrgAdmin,
            UUID organizationId,
            UUID branchId
    ) {

        EventFilter filter = new EventFilter();
        filter.setStartDateFrom(LocalDateTime.now());

        long upcomingEvents =
                eventRepository.count(
                        EventSpecification.filter(
                                organizationId,
                                branchId,
                                !isOrgAdmin,
                                filter.getName(),
                                filter.getStatus(),
                                filter.getStartDateFrom(),
                                filter.getStartDateTo()
                        )
                );

        return EventsCard.builder()
                .upcomingEvents(upcomingEvents)
                .build();
    }

    private FinancialMovementSummaryResponse buildFinanceCard() {

        LocalDate today = LocalDate.now();
        LocalDate firstDayOfMonth = today.withDayOfMonth(1);

        FinancialMovementFilter filter = new FinancialMovementFilter();
        filter.setStartDate(firstDayOfMonth);
        filter.setEndDate(today);

        return financialMovementService.summary(filter);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FinancialMovementResponse> getFinancialMovementsReport(ReportFilterRequest filter) {

        accessGuard.assertCanUse();
        assertModuleActive("FINANCIAL_MOVEMENT");

        UUID branchId = resolveEffectiveBranchId(filter.getBranchId());

        FinancialMovementFilter movementFilter = new FinancialMovementFilter();
        movementFilter.setBranchId(branchId);
        movementFilter.setStartDate(filter.getStartDate());
        movementFilter.setEndDate(filter.getEndDate());

        FinancialMovementSearchRequest request = new FinancialMovementSearchRequest();
        request.setFilters(movementFilter);

        pe.dcs.app.util.pagination.PaginationRequest pagination =
                new pe.dcs.app.util.pagination.PaginationRequest();
        pagination.setPage(0);
        pagination.setSize(10000);
        request.setPagination(pagination);

        pe.dcs.app.util.pagination.SortRequest sort =
                new pe.dcs.app.util.pagination.SortRequest();
        sort.setKey("movementDate");
        sort.setDirection("DESC");
        request.setSorts(List.of(sort));

        return financialMovementService.search(request).getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventResponse> getEventsReport(ReportFilterRequest filter) {

        accessGuard.assertCanUse();
        assertModuleActive("EVENTS");

        boolean isOrgAdmin = authContext.isCurrentOrganizationAdmin();
        UUID organizationId = authContext.getCurrentOrganizationId();
        UUID currentBranchId = authContext.getCurrentBranchId();
        UUID selectedBranchId = isOrgAdmin ? filter.getBranchId() : currentBranchId;

        LocalDateTime startDateTime =
                filter.getStartDate() != null ? filter.getStartDate().atStartOfDay() : null;
        LocalDateTime endDateTime =
                filter.getEndDate() != null ? filter.getEndDate().atTime(23, 59, 59) : null;

        Specification<Event> spec = EventSpecification.filter(
                organizationId,
                currentBranchId,
                !isOrgAdmin,
                null,
                null,
                startDateTime,
                endDateTime
        );

        /*
         * EventSpecification no soporta acotar por sede cuando
         * restrictToBranch=false (org admin ve todo, ver comentario
         * en EventSpecification.filter()) — se combina acá con un
         * predicado adicional en vez de tocar la clase compartida
         * (la usan EventServiceImpl.search, buildEventsCard y
         * DashboardServiceImpl.findNextEvent).
         */
        if (isOrgAdmin && selectedBranchId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("branch").get("id"), selectedBranchId));
        }

        boolean showAudit = authContext.canViewAudit();

        return eventRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "startDateTime"))
                .stream()
                .map(event -> eventMapper.simple(event, showAudit))
                .collect(Collectors.toList());
    }

    /**
     * Branch admin siempre queda forzado a su propia sede sin
     * importar qué branchId venga en el filtro (defensa en
     * profundidad — el frontend ya no debería mostrarle el selector,
     * ver AdvancedReportsAccessGuard). Solo org admin puede acotar
     * libremente a una sede de su organización.
     */
    private UUID resolveEffectiveBranchId(UUID requestedBranchId) {

        if (authContext.isCurrentOrganizationAdmin()) {
            return requestedBranchId;
        }

        return authContext.getCurrentBranchId();
    }

    /**
     * Defensa en profundidad para las tablas de detalle: getExecutiveDashboard()
     * ya oculta la tarjeta/tabla si el módulo no está en el contrato
     * activo (ver activeModules arriba), pero eso es solo a nivel de
     * UI — sin este chequeo, alguien podía llamar directo, por
     * ejemplo, POST /advanced-reports/inventory-movements con un
     * contrato que no incluye Inventario y de todas formas recibir
     * los datos. Mismo código de módulo que activeModules.contains(...)
     * usa en getExecutiveDashboard().
     */
    private void assertModuleActive(String moduleCode) {

        Set<String> activeModules =
                Set.copyOf(contractService.getActiveModuleCodesForCurrentContext());

        if (!activeModules.contains(moduleCode)) {
            throw new Exceptions(
                    "error.moduloSolicitadoNoActivoContratoActual",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    /**
     * Todas las tablas de detalle de Reportes Avanzados traen hasta
     * 10000 filas en una sola página (mismo criterio que
     * getFinancialMovementsReport) — el filtro de fecha/sede ya
     * acota el volumen, y el front las pagina en cliente.
     */
    private PaginationRequest reportPagination() {

        PaginationRequest pagination = new PaginationRequest();
        pagination.setPage(0);
        pagination.setSize(10000);

        return pagination;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MembershipSearchRowResponse> getMembershipReport(ReportFilterRequest filter) {

        accessGuard.assertCanUse();
        assertModuleActive("MEMBERSHIP");

        MembershipFilterRequest membershipFilter = new MembershipFilterRequest();
        membershipFilter.setBranchId(resolveEffectiveBranchId(filter.getBranchId()));
        membershipFilter.setStartDate(filter.getStartDate());
        membershipFilter.setEndDate(filter.getEndDate());

        MembershipSearchRequest request = new MembershipSearchRequest();
        request.setFilters(membershipFilter);
        request.setPagination(reportPagination());

        return membershipService.search(request).getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BaptismSearchRowResponse> getBaptismReport(ReportFilterRequest filter) {

        accessGuard.assertCanUse();
        assertModuleActive("BAPTISM");

        BaptismFilterRequest baptismFilter = new BaptismFilterRequest();
        baptismFilter.setBranchId(resolveEffectiveBranchId(filter.getBranchId()));
        baptismFilter.setStartDate(filter.getStartDate());
        baptismFilter.setEndDate(filter.getEndDate());

        BaptismSearchRequest request = new BaptismSearchRequest();
        request.setFilters(baptismFilter);
        request.setPagination(reportPagination());

        return baptismService.search(request).getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MarriageSearchRowResponse> getMarriagesReport(ReportFilterRequest filter) {

        accessGuard.assertCanUse();
        assertModuleActive("MARRIAGE");

        MarriageFilterRequest marriageFilter = new MarriageFilterRequest();
        marriageFilter.setBranchId(resolveEffectiveBranchId(filter.getBranchId()));
        marriageFilter.setStartDate(filter.getStartDate());
        marriageFilter.setEndDate(filter.getEndDate());

        MarriageSearchRequest request = new MarriageSearchRequest();
        request.setFilters(marriageFilter);
        request.setPagination(reportPagination());

        return marriageService.search(request).getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VisitorSearchRowResponse> getVisitorsReport(ReportFilterRequest filter) {

        accessGuard.assertCanUse();
        assertModuleActive("VISITOR");

        VisitorFilterRequest visitorFilter = new VisitorFilterRequest();
        visitorFilter.setBranchId(resolveEffectiveBranchId(filter.getBranchId()));
        visitorFilter.setStartDate(filter.getStartDate());
        visitorFilter.setEndDate(filter.getEndDate());

        VisitorSearchRequest request = new VisitorSearchRequest();
        request.setFilters(visitorFilter);
        request.setPagination(reportPagination());

        return visitorService.search(request).getContent();
    }

    /**
     * Miembros Inactivos no tiene rango de fechas propio (se basa en
     * un umbral de inactividad, no en un período de registros) — el
     * filtro de fecha del reporte se ignora acá, solo se usa
     * branchId.
     */
    @Override
    @Transactional(readOnly = true)
    public List<InactiveMemberResponse> getInactiveMembersReport(ReportFilterRequest filter) {

        accessGuard.assertCanUse();
        assertModuleActive("PASTORAL_FOLLOWUP");

        InactiveMemberFilterRequest inactiveFilter = new InactiveMemberFilterRequest();
        inactiveFilter.setBranchId(resolveEffectiveBranchId(filter.getBranchId()));

        InactiveMemberSearchRequest request = new InactiveMemberSearchRequest();
        request.setFilters(inactiveFilter);
        request.setPagination(reportPagination());

        return pastoralFollowUpService.searchInactiveMembers(request).getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SmallGroupSearchRowResponse> getSmallGroupsReport(ReportFilterRequest filter) {

        accessGuard.assertCanUse();
        assertModuleActive("SMALL_GROUP");

        SmallGroupFilterRequest smallGroupFilter = new SmallGroupFilterRequest();
        smallGroupFilter.setBranchId(resolveEffectiveBranchId(filter.getBranchId()));
        smallGroupFilter.setStartDate(filter.getStartDate());
        smallGroupFilter.setEndDate(filter.getEndDate());

        SmallGroupSearchRequest request = new SmallGroupSearchRequest();
        request.setFilters(smallGroupFilter);
        request.setPagination(reportPagination());

        return smallGroupService.search(request).getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpaceReservationResponse> getSpaceReservationsReport(ReportFilterRequest filter) {

        accessGuard.assertCanUse();
        assertModuleActive("SPACE_RESERVATION");

        SpaceReservationFilterRequest reservationFilter = new SpaceReservationFilterRequest();
        reservationFilter.setBranchId(resolveEffectiveBranchId(filter.getBranchId()));

        if (filter.getStartDate() != null) {
            reservationFilter.setFromDateTime(filter.getStartDate().atStartOfDay());
        }

        if (filter.getEndDate() != null) {
            reservationFilter.setToDateTime(filter.getEndDate().atTime(23, 59, 59));
        }

        SpaceReservationSearchRequest request = new SpaceReservationSearchRequest();
        request.setFilters(reservationFilter);
        request.setPagination(reportPagination());

        return spaceReservationService.searchReservations(request).getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryMovementResponse> getInventoryMovementsReport(ReportFilterRequest filter) {

        accessGuard.assertCanUse();
        assertModuleActive("INVENTORY");

        InventoryMovementFilterRequest movementFilter = new InventoryMovementFilterRequest();
        movementFilter.setBranchId(resolveEffectiveBranchId(filter.getBranchId()));
        movementFilter.setFromDate(filter.getStartDate());
        movementFilter.setToDate(filter.getEndDate());

        InventoryMovementSearchRequest request = new InventoryMovementSearchRequest();
        request.setFilters(movementFilter);
        request.setPagination(reportPagination());

        return inventoryService.searchMovements(request).getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> getLeaveRequestsReport(ReportFilterRequest filter) {

        accessGuard.assertCanUse();
        assertModuleActive("LEAVE_REQUEST");

        LeaveRequestFilterRequest leaveFilter = new LeaveRequestFilterRequest();
        leaveFilter.setBranchId(resolveEffectiveBranchId(filter.getBranchId()));
        leaveFilter.setFromDate(filter.getStartDate());
        leaveFilter.setToDate(filter.getEndDate());

        LeaveRequestSearchRequest request = new LeaveRequestSearchRequest();
        request.setFilters(leaveFilter);
        request.setPagination(reportPagination());

        return hrService.searchLeaveRequests(request).getContent();
    }
}
