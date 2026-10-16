package pe.dcs.app.features.auth.dto;

import java.util.UUID;

public record UserInfo(UUID id, String name, String username, String actorType, boolean mfaEnabled) {
}
