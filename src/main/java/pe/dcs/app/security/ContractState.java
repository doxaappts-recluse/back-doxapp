package pe.dcs.app.security;

/** Claim contractState (spec 00 §5). Sin contrato ACTIVE: ORG_ADMIN entra en modo limitado; el resto se bloquea. */
public enum ContractState {
    ACTIVE, NONE
}
