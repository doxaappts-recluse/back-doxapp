package pe.dcs.app.features.support.domain;

/** CONTRACT_CHANGE y NEW_BRANCH solo las abre el administrador de la organización. */
public enum SupportCategory {
    HOW_TO, BUG, BILLING, CONTRACT_CHANGE, NEW_BRANCH, DATA_REQUEST, OTHER;

    public boolean orgAdminOnly() {
        return this == CONTRACT_CHANGE || this == NEW_BRANCH;
    }
}
