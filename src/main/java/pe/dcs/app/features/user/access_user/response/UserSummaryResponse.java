package pe.dcs.app.features.user.access_user.response;

import java.util.UUID;

public record UserSummaryResponse(
        UUID id,
        String username,
        String firstName,
        String lastName
) {}