package pe.dcs.app.features.user.access_user.response;

import java.util.List;
import java.util.UUID;

public record ModuleAccessConfigResponse(
        UUID moduleId,
        String moduleName,
        boolean assigned,
        List<PermissionConfigResponse> permissions
) {}