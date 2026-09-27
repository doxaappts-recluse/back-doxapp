package pe.dcs.app.features.support.dto;

import java.util.UUID;

/** assigneeId nulo = liberar el caso. */
public record AssignRequest(UUID assigneeId) {
}
