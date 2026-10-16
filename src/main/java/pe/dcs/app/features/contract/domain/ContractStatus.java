package pe.dcs.app.features.contract.domain;

/** Estados de contrato (spec 00 §9). Solo ACTIVE habilita módulos contratables. */
public enum ContractStatus {
    PENDING, ACTIVE, SUSPENDED, EXPIRED, CANCELLED, REPLACED
}
