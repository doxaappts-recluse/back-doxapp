package pe.dcs.app.features.support.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Caso para la organización (N2/N3). Sin SLA, asignación interna ni notas internas. messages solo va en el detalle. */
public record OrgCaseResponse(
        UUID id, long caseNumber, String code, UUID branchId, String branchName, UUID openedById, String openedByName,
        String assigneeName, String category, String priority, String subject, String status,
        Instant createdAt, Instant lastActivityAt, Instant resolvedAt, Instant closedAt,
        Integer satisfaction, String satisfactionComment, boolean canReply, boolean canRate, Long version,
        List<OrgMessage> messages
) {
}
