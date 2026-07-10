package pe.dcs.app.features.auth.response;

import pe.dcs.app.security.service.UserAccessContext;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JwtResponse(

        String token,

        UUID userId,

        String username,

        String name,

        String lastname,

        List<String> roles,

        List<UserAccessContext> accesses,

        UUID currentOrganizationId,

        UUID currentBranchId,

        Instant emissionTime,

        Instant expirationTime

) {
}