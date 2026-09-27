package pe.dcs.app.features.organization.domain;

/** Ciclo de vida de organización (M02): DRAFT → (TRIAL) → ACTIVE ⇄ SUSPENDED → CLOSED. */
public enum OrganizationStatus {
    DRAFT, TRIAL, ACTIVE, SUSPENDED, CLOSED;

    public boolean allowsLogin() {
        return this != SUSPENDED && this != CLOSED;
    }
}
