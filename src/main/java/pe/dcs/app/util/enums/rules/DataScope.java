package pe.dcs.app.util.enums.rules;

public enum DataScope {

    /** Ve toda la organización. */
    ORGANIZATION,
    /** Solo la sede actual. */
    CURRENT_BRANCH,
    /** Puede ver sedes donde estuvo. */
    PERSON_HISTORY,
    /** Requiere autorización. */
    APPROVAL_REQUIRED

}