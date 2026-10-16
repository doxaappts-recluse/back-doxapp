package pe.dcs.app.features.support.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Caso para el personal de plataforma. messages (con notas internas) solo va en el detalle. */
public record StaffCaseResponse(
        UUID id, long caseNumber, String code, UUID organizationId, String organizationName, UUID branchId, String branchName,
        UUID openedById, String openedByName, UUID assigneeId, String assigneeName, String category, String priority,
        String subject, String status, Instant slaDueAt, Instant firstResponseAt, boolean slaBreached,
        Instant createdAt, Instant lastActivityAt, Instant resolvedAt, Instant closedAt,
        Integer satisfaction, String satisfactionComment, Long version, List<StaffMessage> messages
) {
}
