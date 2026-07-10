package pe.dcs.app.util.constant;

public final class RoleConstant {

    private RoleConstant(){}

    public static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";
    public static final String SYSTEM_SUPPORT = "SYSTEM_SUPPORT";

    public static final String ORG_ADMIN = "ORG_ADMIN";
    public static final String ORG_BRANCH_ADMIN = "ORG_BRANCH_ADMIN";
    public static final String ORG_USER = "ORG_USER";

    public static boolean isSystem(String role){
        return SYSTEM_ADMIN.equals(role)
                || SYSTEM_SUPPORT.equals(role);
    }

    public static boolean isOrganization(String role){
        return ORG_ADMIN.equals(role)
                || ORG_BRANCH_ADMIN.equals(role)
                || ORG_USER.equals(role);
    }

    public static boolean isBranchRole(String role){
        return ORG_BRANCH_ADMIN.equals(role)
                || ORG_USER.equals(role);
    }

}