package pe.dcs.app.features.access.domain;

import pe.dcs.app.util.enums.RoleType;

public enum StaffRole {
    SYSTEM_ADMIN, SYSTEM_SUPPORT;

    public RoleType toRoleType() {
        return this == SYSTEM_ADMIN ? RoleType.SYSTEM_ADMIN : RoleType.SYSTEM_SUPPORT;
    }
}
