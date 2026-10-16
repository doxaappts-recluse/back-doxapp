package pe.dcs.app.util.enums;

/**
 * Roles del sistema (spec 00 §4). Nivel: N1 plataforma · N2 organización · N3 sede/apoyo · N4 portal.
 */
public enum RoleType {
    SYSTEM_ADMIN(1),
    SYSTEM_SUPPORT(1),
    ORG_ADMIN(2),
    ORG_BRANCH_ADMIN(3),
    ORG_USER(3),
    MEMBER(4);

    private final int level;

    RoleType(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    public String levelCode() {
        return "N" + level;
    }

    public boolean isStaff() {
        return level == 1;
    }
}
