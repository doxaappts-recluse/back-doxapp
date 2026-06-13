package pe.dcs.app.features.user.access_user.request;

import java.util.List;
import java.util.UUID;

public record UserAccessUpsertRequest(
        UUID contractId,
        List<UserModuleAccessRequest> modules
) {}