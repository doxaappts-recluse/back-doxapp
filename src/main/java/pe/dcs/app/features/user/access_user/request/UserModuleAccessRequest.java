package pe.dcs.app.features.user.access_user.request;

import java.util.List;
import java.util.UUID;

public record UserModuleAccessRequest(
        UUID moduleId,
        List<UUID> permissionIds
) {}