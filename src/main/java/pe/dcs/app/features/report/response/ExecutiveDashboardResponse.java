package pe.dcs.app.features.report.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.features.finance.response.FinancialBudgetProgressResponse;
import pe.dcs.app.features.finance.response.FinancialMovementSummaryResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Dashboard Ejecutivo (Reportes Avanzados). Cada tarjeta es null si
 * el módulo correspondiente no está activo en el contrato actual
 * (org o sede) — ver ContractService.getActiveModuleCodesForCurrentContext()
 * y AdvancedReportsServiceImpl.build(). El alcance (org-wide vs solo
 * una sede) lo fija AdvancedReportsAccessGuard/AuthContext, igual que
 * EventDashboardServiceImpl: org admin ve toda la organización,
 * branch admin solo su sede.
 */
@Getter
@Setter
@Builder
public class ExecutiveDashboardResponse {

    /** "ORGANIZATION" si lo ve un org admin, "BRANCH" si es un branch admin. */
    private String scope;

    private UUID branchId;
    private String branchName;

    private LocalDateTime generatedAt;

    private MembershipCard membership;
    private BaptismCard baptism;
    private MarriageCard marriage;
    private VisitorCard visitor;
    private PastoralFollowUpCard pastoralFollowUp;
    private SmallGroupCard smallGroup;
    private BibleAcademyCard bibleAcademy;
    private SpaceReservationCard spaceReservation;
    private InventoryCard inventory;
    private HrCard hr;
    private EventsCard events;

    private FinancialMovementSummaryResponse finance;
    private List<FinancialBudgetProgressResponse> budgets;
}
