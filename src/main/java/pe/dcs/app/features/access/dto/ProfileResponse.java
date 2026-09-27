package pe.dcs.app.features.access.dto;

import pe.dcs.app.util.enums.StatusType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProfileResponse(UUID id, String name, String description, StatusType status, List<PermissionItemDto> items,
                              Instant createdAt, Instant updatedAt, Long version) {
}
