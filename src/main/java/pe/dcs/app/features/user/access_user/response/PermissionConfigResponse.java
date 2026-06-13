package pe.dcs.app.features.user.access_user.response;

import java.util.UUID;

public record PermissionConfigResponse(
        UUID permissionId,
        String code,
        boolean assigned
) {}