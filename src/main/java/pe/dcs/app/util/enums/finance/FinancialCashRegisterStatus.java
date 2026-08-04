package pe.dcs.app.util.enums.finance;

/**
 * Estado de una caja diaria — no reutiliza StatusType (ACTIVE/
 * INACTIVE) porque la semántica es distinta: OPEN/CLOSED describe
 * un ciclo de vida de un único uso (se abre, se opera, se cierra
 * con arqueo), no un catálogo habilitable/inhabilitable.
 */
public enum FinancialCashRegisterStatus {
    OPEN,
    CLOSED
}
