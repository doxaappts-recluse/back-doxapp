package pe.dcs.app.features.access_user.response;

import java.util.List;
import java.util.UUID;

public record UserAccessConfigResponse(
        UserSummaryResponse user,
        UUID contractId,
        List<ModuleAccessConfigResponse> modules,
        List<ModuleAccessConfigResponse> assignedModules
) {}