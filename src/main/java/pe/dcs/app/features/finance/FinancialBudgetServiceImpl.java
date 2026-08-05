package pe.dcs.app.features.finance;

import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.FinancialBudget;
import pe.dcs.app.entity.FinancialFund;
import pe.dcs.app.entity.FinancialMovement;
import pe.dcs.app.entity.Organization;
import pe.dcs.app.features.finance.request.FinancialBudgetRequest;
import pe.dcs.app.features.finance.response.FinancialBudgetProgressResponse;
import pe.dcs.app.features.finance.response.FinancialBudgetResponse;
import pe.dcs.app.repository.BranchRepository;
import pe.dcs.app.repository.FinancialBudgetRepository;
import pe.dcs.app.repository.FinancialFundRepository;
import pe.dcs.app.repository.FinancialMovementRepository;
import pe.dcs.app.repository.OrganizationRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.finance.FinancialMovementStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinancialBudgetServiceImpl implements FinancialBudgetService {

    private final FinancialBudgetRepository financialBudgetRepository;
    private final FinancialMovementRepository financialMovementRepository;
    private final FinancialFundRepository financialFundRepository;
    private final BranchRepository branchRepository;
    private final OrganizationRepository organizationRepository;
    private final AuthContext authContext;
    private final FinancialBudgetMapper financialBudgetMapper;

    /**
     * Definir presupuestos es una decisión administrativa de la
     * organización, igual criterio que Fondos: solo org admin (o
     * SYSTEM) gestiona el catálogo.
     */
    private void assertCanManage() {

        if (authContext.isSystem()
                || authContext.isCurrentOrganizationAdmin()) {
            return;
        }

        throw new Exceptions(
                "error.soloAdministradorOrganizacionPuedeGestionarPresupuestos",
                HttpStatus.FORBIDDEN
        );
    }

    private UUID currentOrganizationId() {

        UUID organizationId = authContext.getCurrentOrganizationId();

        if (organizationId == null) {
            throw new Exceptions(
                    "error.noPudoDeterminarOrganizacionActual",
                    HttpStatus.BAD_REQUEST
            );
        }

        return organizationId;
    }

    private FinancialBudget findOwn(UUID id) {

        FinancialBudget budget =
                financialBudgetRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.presupuestoNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (!authContext.isSystem()
                && !budget.getOrganization().getId().equals(currentOrganizationId())) {

            throw new Exceptions(
                    "error.noTieneAccesoPresupuesto",
                    HttpStatus.FORBIDDEN
            );
        }

        return budget;
    }

    private Branch findBranch(UUID branchId, UUID organizationId) {

        Branch branch =
                branchRepository.findById(branchId)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.sedeNoEncontrada",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (!branch.getOrganization().getId().equals(organizationId)) {
            throw new Exceptions(
                    "error.sedeNoPerteneceOrganizacionActual",
                    HttpStatus.BAD_REQUEST
            );
        }

        return branch;
    }

    private FinancialFund findFund(UUID fundId, UUID organizationId) {

        FinancialFund fund =
                financialFundRepository.findById(fundId)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.fondoNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (!fund.getOrganization().getId().equals(organizationId)) {
            throw new Exceptions(
                    "error.fondoNoPerteneceOrganizacionActual",
                    HttpStatus.BAD_REQUEST
            );
        }

        return fund;
    }

    private void validatePeriod(FinancialBudgetRequest request) {

        if (request.getPeriodYear() == null || request.getPeriodMonth() == null) {
            throw new Exceptions(
                    "error.debeIndicarPeriodoAnoMesPresupuesto",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (request.getPeriodMonth() < 1 || request.getPeriodMonth() > 12) {
            throw new Exceptions(
                    "error.mesPeriodoDebeEstarEntre1",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    @Override
    @Transactional
    public FinancialBudgetResponse create(FinancialBudgetRequest request) {

        assertCanManage();
        validatePeriod(request);

        UUID organizationId = currentOrganizationId();

        Organization organization =
                organizationRepository.findById(organizationId)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.organizacionNoEncontrada",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        FinancialBudget budget = new FinancialBudget();

        budget.setOrganization(organization);
        applyRequest(budget, request, organizationId);
        budget.setStatus(StatusType.ACTIVE);

        return financialBudgetMapper.simple(
                financialBudgetRepository.save(budget),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional
    public FinancialBudgetResponse update(UUID id, FinancialBudgetRequest request) {

        assertCanManage();
        validatePeriod(request);

        FinancialBudget budget = findOwn(id);

        applyRequest(budget, request, budget.getOrganization().getId());
        budget.setUpdatedAt(Instant.now());

        return financialBudgetMapper.simple(
                financialBudgetRepository.save(budget),
                authContext.canViewAudit()
        );
    }

    private void applyRequest(FinancialBudget budget, FinancialBudgetRequest request, UUID organizationId) {

        budget.setName(request.getName());
        budget.setDescription(request.getDescription());
        budget.setAmount(request.getAmount());
        budget.setPeriodYear(request.getPeriodYear());
        budget.setPeriodMonth(request.getPeriodMonth());
        budget.setCategory(request.getCategory());

        budget.setBranch(
                request.getBranchId() != null
                        ? findBranch(request.getBranchId(), organizationId)
                        : null
        );

        budget.setFund(
                request.getFundId() != null
                        ? findFund(request.getFundId(), organizationId)
                        : null
        );
    }

    @Override
    @Transactional
    public FinancialBudgetResponse enable(UUID id) {

        assertCanManage();

        FinancialBudget budget = findOwn(id);

        budget.setStatus(StatusType.ACTIVE);
        budget.setUpdatedAt(Instant.now());

        return financialBudgetMapper.simple(
                financialBudgetRepository.save(budget),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional
    public FinancialBudgetResponse disable(UUID id) {

        assertCanManage();

        FinancialBudget budget = findOwn(id);

        budget.setStatus(StatusType.INACTIVE);
        budget.setUpdatedAt(Instant.now());

        return financialBudgetMapper.simple(
                financialBudgetRepository.save(budget),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialBudgetResponse getById(UUID id) {

        assertCanManage();

        return financialBudgetMapper.simple(
                findOwn(id),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<FinancialBudgetResponse> listAll() {

        assertCanManage();

        boolean showAudit = authContext.canViewAudit();

        return financialBudgetRepository
                .findByOrganizationIdOrderByPeriodYearDescPeriodMonthDescNameAsc(
                        currentOrganizationId()
                )
                .stream()
                .map(b -> financialBudgetMapper.simple(b, showAudit))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialBudgetProgressResponse progress(UUID id) {

        assertCanManage();

        return buildProgress(findOwn(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FinancialBudgetProgressResponse> progressForPeriod(Integer periodYear, Integer periodMonth) {

        assertCanManage();

        if (periodYear == null || periodMonth == null) {
            throw new Exceptions(
                    "error.debeIndicarPeriodoAnoMes",
                    HttpStatus.BAD_REQUEST
            );
        }

        return financialBudgetRepository
                .findByOrganizationIdAndStatusAndPeriodYearAndPeriodMonth(
                        currentOrganizationId(),
                        StatusType.ACTIVE,
                        periodYear,
                        periodMonth
                )
                .stream()
                .map(this::buildProgress)
                .toList();
    }

    /**
     * Suma los movimientos APROBADOS del mismo scope (sede/fondo/
     * categoría) y mes calendario del presupuesto — mismo criterio
     * de "solo APROBADOS" que FinancialMovementServiceImpl.summary()
     * (un PENDING no es plata real todavía, un REJECTED nunca
     * ocurrió).
     */
    private FinancialBudgetProgressResponse buildProgress(FinancialBudget budget) {

        YearMonth yearMonth = YearMonth.of(budget.getPeriodYear(), budget.getPeriodMonth());
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        Specification<FinancialMovement> spec =
                FinancialMovementSpecification.filter(
                        authContext,
                        budget.getBranch() != null ? budget.getBranch().getId() : null,
                        null,
                        budget.getCategory(),
                        FinancialMovementStatus.APPROVED,
                        null,
                        budget.getFund() != null ? budget.getFund().getId() : null,
                        startDate,
                        endDate,
                        null
                );

        BigDecimal actualAmount = financialMovementRepository.findAll(spec).stream()
                .map(FinancialMovement::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal percentageUsed =
                budget.getAmount() != null && budget.getAmount().compareTo(BigDecimal.ZERO) > 0
                        ? actualAmount
                                .multiply(BigDecimal.valueOf(100))
                                .divide(budget.getAmount(), 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        return FinancialBudgetProgressResponse.builder()
                .budgetId(budget.getId())
                .name(budget.getName())
                .budgetedAmount(budget.getAmount())
                .actualAmount(actualAmount)
                .percentageUsed(percentageUsed)
                .overBudget(actualAmount.compareTo(budget.getAmount()) > 0)
                .build();
    }
}
