package pe.dcs.app.features.profile.response;

import pe.dcs.app.util.enums.StatusType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ProfileResponse(
        UUID id,
        String username,
        String name,
        String lastname,
        String dni,
        String phone,
        String address,
        String sex,
        String dateBirth,
        String maritalStatus,
        String children,
        String dateAdmission,
        List<ProfileAccessResponse> accesses,
        StatusType status,
        Instant createdAt,
        Instant updatedAt
){}