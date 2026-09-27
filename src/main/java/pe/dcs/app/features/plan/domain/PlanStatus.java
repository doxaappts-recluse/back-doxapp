package pe.dcs.app.features.plan.domain;

/** DRAFT → PUBLISHED → RETIRED. Un plan retirado no se ofrece a contratos nuevos. */
public enum PlanStatus {
    DRAFT, PUBLISHED, RETIRED
}
