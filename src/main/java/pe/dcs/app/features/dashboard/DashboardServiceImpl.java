package pe.dcs.app.features.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.Contract;
import pe.dcs.app.entity.Event;
import pe.dcs.app.entity.Module;
import pe.dcs.app.features.contract.service.ContractService;
import pe.dcs.app.features.dashboard.response.DashboardHomeResponse;
import pe.dcs.app.features.dashboard.response.NextEventResponse;
import pe.dcs.app.features.event.specification.EventSpecification;
import pe.dcs.app.features.finance.FinancialCashRegisterService;
import pe.dcs.app.features.finance.FinancialMovementSpecification;
import pe.dcs.app.features.hr.LeaveRequestSpecification;
import pe.dcs.app.features.hr.request.LeaveRequestFilterRequest;
import pe.dcs.app.features.membership.MembershipSpecification;
import pe.dcs.app.features.membership.request.MembershipFilterRequest;
import pe.dcs.app.features.membership.request.MembershipSearchRequest;
import pe.dcs.app.features.module.ContractResolver;
import pe.dcs.app.features.user.system_user.UserSystemSpecification;
import pe.dcs.app.features.user.system_user.request.UserSystemListRequest;
import pe.dcs.app.repository.BranchRepository;
import pe.dcs.app.repository.ContractModuleRepository;
import pe.dcs.app.repository.ContractRepository;
import pe.dcs.app.repository.EventRepository;
import pe.dcs.app.repository.FinancialMovementRepository;
import pe.dcs.app.repository.LeaveRequestRepository;
import pe.dcs.app.repository.ModuleRepository;
import pe.dcs.app.repository.OrganizationRepository;
import pe.dcs.app.repository.PersonRepository;
import pe.dcs.app.repository.UserAccessModuleRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.contract.ContractStatus;
import pe.dcs.app.util.enums.events.EventStatus;
import pe.dcs.app.util.enums.finance.FinancialMovementStatus;
import pe.dcs.app.util.enums.hr.HrApprovalStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dashboard de bienvenida — cubre los 4 roles (ver
 * DashboardAccessGuard, el único de los dashboards que se abre a
 * TODOS, incluido SYSTEM y org user). A diferencia de
 * {@link pe.dcs.app.features.report.AdvancedReportsServiceImpl}
 * (grilla completa de tarjetas, exclusivo de admins), acá solo se
 * calcula lo accionable del día a día, reutilizando al máximo las
 * Specification/Service ya existentes — que además de por sí
 * auto-escalan por AuthContext (ORGANIZATION para org admin, sede
 * actual para el resto, ver MembershipSpecification/
 * FinancialMovementSpecification/LeaveRequestSpecification), así
 * que las mismas queries sirven igual para branch admin y para org
 * user sin duplicar nada. Lo único que cambia por rol es QUÉ
 * módulos gatean cada sección:
 * - org admin / branch admin: todo lo contratado (ver
 *   ContractService.getActiveModuleCodesForCurrentContext()).
 * - org user: solo lo que tiene delegado a título personal (ver
 *   resolveUserAccessibleModules(), mismo criterio que
 *   SidebarService para el árbol de navegación de ORG_USER).
 * - SYSTEM: no tiene org/sede — ve un resumen de plataforma aparte
 *   (buildSystemHome()), sin ninguna de las secciones de arriba.
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final DashboardAccessGuard accessGuard;
    private final AuthContext authContext;
    private final ContractService contractService;
    private final BranchRepository branchRepository;

    private final PersonRepository personRepository;
    private final EventRepository eventRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final FinancialMovementRepository financialMovementRepository;

    private final FinancialCashRegisterService financialCashRegisterService;

    private final UserAccessModuleRepository userAccessModuleRepository;
    private final ContractResolver contractResolver;
    private final ContractModuleRepository contractModuleRepository;
    private final ModuleRepository moduleRepository;

    private final OrganizationRepository organizationRepository;
    private final ContractRepository contractRepository;

    @Override
    @Transactional(readOnly = true)
    public DashboardHomeResponse getHome() {

        accessGuard.assertCanUse();

        if (authContext.isSystem()) {
            return buildSystemHome();
        }

        boolean isOrgAdmin = authContext.isCurrentOrganizationAdmin();
        boolean isBranchAdmin = authContext.isCurrentBranchAdmin();
        UUID organizationId = authContext.getCurrentOrganizationId();
        UUID branchId = authContext.getCurrentBranchId();

        String scope;
        Set<String> visibleModules;

        if (isOrgAdmin || isBranchAdmin) {
            scope = isOrgAdmin ? "ORGANIZATION" : "BRANCH";
            visibleModules = Set.copyOf(contractService.getActiveModuleCodesForCurrentContext());
        } else {
            scope = "USER";
            visibleModules = resolveUserAccessibleModules(organizationId, branchId);
        }

        DashboardHomeResponse.DashboardHomeResponseBuilder builder =
                DashboardHomeResponse.builder()
                        .scope(scope)
                        .branchId(branchId)
                        .generatedAt(LocalDateTime.now());

        if (!isOrgAdmin && branchId != null) {
            branchRepository.findById(branchId)
                    .map(Branch::getName)
                    .ifPresent(builder::branchName);
        }

        if (visibleModules.contains("MEMBERSHIP")) {
            builder.activeMembers(countActiveMembers());
        }

        if (visibleModules.contains("EVENTS")) {
            builder.nextEvent(findNextEvent(isOrgAdmin, organizationId, branchId));
        }

        if (visibleModules.contains("LEAVE_REQUEST")) {
            builder.pendingLeaveRequests(countPendingLeaveRequests());
        }

        if (visibleModules.contains("FINANCIAL_MOVEMENT")) {
            builder.pendingFinancialMovements(countPendingFinancialMovements());
        }

        if (visibleModules.contains("FINANCIAL_CASH_REGISTER") && branchId != null) {
            builder.openCashRegister(financialCashRegisterService.getOpenByBranch(branchId));
        }

        builder.activeModules(List.copyOf(visibleModules));

        return builder.build();
    }

    /**
     * Resumen de plataforma para SYSTEM: sin org/sede, así que
     * ninguna de las Specifications de arriba aplica (todas
     * escalan sobre organización/sede actual). Se limita a conteos
     * simples reutilizando repositorios ya existentes — mismo
     * criterio de "solo lo accionable" que el resto del dashboard,
     * pero a nivel plataforma en vez de por módulo contratado.
     */
    private DashboardHomeResponse buildSystemHome() {

        return DashboardHomeResponse.builder()
                .scope("SYSTEM")
                .generatedAt(LocalDateTime.now())
                .organizationsCount(organizationRepository.count())
                .activeContractsCount(countActiveContractsPlatformWide())
                .contractsExpiringSoon(countContractsExpiringSoon())
                .systemUsersCount(
                        personRepository.count(
                                UserSystemSpecification.filter(new UserSystemListRequest())
                        )
                )
                .activeModules(List.of())
                .build();
    }

    private long countActiveContractsPlatformWide() {

        LocalDate today = LocalDate.now();

        return contractRepository.count(activeContractOn(today));
    }

    private long countContractsExpiringSoon() {

        LocalDate today = LocalDate.now();
        LocalDate soon = today.plusDays(30);

        return contractRepository.count(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("status"), ContractStatus.ACTIVE),
                        cb.greaterThanOrEqualTo(root.get("endDate"), today),
                        cb.lessThanOrEqualTo(root.get("endDate"), soon)
                )
        );
    }

    private Specification<Contract> activeContractOn(LocalDate date) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), ContractStatus.ACTIVE),
                cb.lessThanOrEqualTo(root.get("startDate"), date),
                cb.greaterThanOrEqualTo(root.get("endDate"), date)
        );
    }

    /**
     * Módulos que el org user tiene delegados a título personal en
     * el acceso (organización + sede) actualmente activo,
     * intersectados con lo que el contrato vigente permite — mismo
     * criterio exacto que SidebarService para armar el árbol de
     * navegación de ORG_USER (ver ahí el comentario de seguridad:
     * solo lo asignado EN ESTA sede/organización puntual, nunca lo
     * de otro acceso de la misma persona).
     */
    private Set<String> resolveUserAccessibleModules(UUID organizationId, UUID branchId) {

        UUID userId = authContext.getUserId();

        if (userId == null || organizationId == null || branchId == null) {
            return Set.of();
        }

        Set<UUID> userModuleIds = new HashSet<>(
                userAccessModuleRepository.findActiveModuleIdsByPersonIdAndOrganizationIdAndBranchId(
                        userId,
                        organizationId,
                        branchId,
                        StatusType.ACTIVE
                )
        );

        Set<UUID> contractModuleIds =
                contractResolver.getActiveContractsByBranch(branchId)
                        .stream()
                        .flatMap(contract ->
                                contractModuleRepository
                                        .findModuleIdsByContractId(contract.getId())
                                        .stream()
                        )
                        .collect(Collectors.toSet());

        userModuleIds.retainAll(contractModuleIds);

        if (userModuleIds.isEmpty()) {
            return Set.of();
        }

        return moduleRepository.findAllById(userModuleIds)
                .stream()
                .map(Module::getCode)
                .collect(Collectors.toSet());
    }

    private long countActiveMembers() {

        MembershipFilterRequest filter = new MembershipFilterRequest();
        filter.setMembershipStatus(StatusType.ACTIVE);

        MembershipSearchRequest request = new MembershipSearchRequest();
        request.setFilters(filter);

        return personRepository.count(
                MembershipSpecification.filter(request, authContext)
        );
    }

    private NextEventResponse findNextEvent(
            boolean isOrgAdmin,
            UUID organizationId,
            UUID branchId
    ) {

        List<Event> nextEvent = eventRepository.findAll(
                EventSpecification.filter(
                        organizationId,
                        branchId,
                        !isOrgAdmin,
                        null,
                        EventStatus.PUBLISHED,
                        LocalDateTime.now(),
                        null
                ),
                PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "startDateTime"))
        ).getContent();

        if (nextEvent.isEmpty()) {
            return null;
        }

        Event event = nextEvent.get(0);

        return NextEventResponse.builder()
                .id(event.getId())
                .name(event.getName())
                .startDateTime(event.getStartDateTime())
                .build();
    }

    private long countPendingLeaveRequests() {

        LeaveRequestFilterRequest filter = new LeaveRequestFilterRequest();
        filter.setStatus(HrApprovalStatus.PENDING);

        return leaveRequestRepository.count(
                LeaveRequestSpecification.filter(filter, authContext)
        );
    }

    private long countPendingFinancialMovements() {

        return financialMovementRepository.count(
                FinancialMovementSpecification.filter(
                        authContext,
                        null,
                        null,
                        null,
                        FinancialMovementStatus.PENDING,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );
    }
}
