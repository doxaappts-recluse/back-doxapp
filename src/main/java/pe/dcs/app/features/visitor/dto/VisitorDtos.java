package pe.dcs.app.features.visitor.dto;

import pe.dcs.app.shared.vo.DocumentType;
import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** M07 · Visitantes y consolidación (/admin/visitors y formulario público). */
public final class VisitorDtos {

    private VisitorDtos() {
    }

    public record Search(Filters filters, PaginationRequest pagination, List<SortRequest> sorts) {
        /** open = solo etapas abiertas (tablero) · mine = asignados a mí · overdue = acción vencida o sin contacto fuera de plazo · unassigned = sin consolidador. */
        public record Filters(String q, UUID branchId, String stage, UUID consolidatorId, Boolean mine, Boolean open, Boolean overdue,
                              Boolean unassigned) {
        }
    }

    /** Registrar visita. Con personId se vincula una persona existente; sin él se crea una (o se propone vincular si hay coincidencias). */
    public record CreateRequest(UUID personId, String firstName, String lastName, String phone, String email, DocumentType docType,
                                String docNumber, UUID branchId, LocalDate firstVisitDate, String howArrived, UUID invitedBy,
                                UUID consolidatorId, String notes, Boolean consentGranted, Boolean ignoreMatches) {
    }

    public record UpdateRequest(LocalDate firstVisitDate, String howArrived, UUID invitedBy, String notes, LocalDate nextActionDate, Long version) {
    }

    /** consolidatorId nulo = quitar la asignación. */
    public record AssignRequest(UUID consolidatorId) {
    }

    public record ContactRequest(String method, String result, String notes, LocalDate nextActionDate) {
    }

    public record ArchiveRequest(String reason) {
    }

    public record FunnelRequest(UUID branchId, LocalDate from, LocalDate to) {
    }

    public record RulesRequest(Integer integratedMinAttendances, Integer integratedWindowWeeks, Integer newSlaHours, Long version) {
    }

    public record Rules(int integratedMinAttendances, int integratedWindowWeeks, int newSlaHours, Long version) {
    }

    public record PersonRef(UUID id, String fullName, String docType, String docNumber, String phone, String email, String status, Integer age) {
    }

    public record Contact(UUID id, Instant at, String method, String result, String notes, LocalDate nextActionDate, UUID byPersonId, String byName) {
    }

    public record Summary(UUID id, UUID personId, String fullName, String phone, String email, UUID branchId, String branchName, String stage,
                          LocalDate firstVisitDate, String howArrived, UUID consolidatorId, String consolidatorName, Instant lastContactAt,
                          LocalDate nextActionDate, boolean overdue, boolean slaBreached, String source, Instant createdAt) {
    }

    public record Response(Summary summary, PersonRef person, UUID invitedById, String invitedByName, String notes, String archiveReason,
                           String consentStatus, Instant consentAt, Instant firstContactAt, Instant integratedAt, Instant closedAt,
                           List<Contact> contacts, boolean canConvert, Long version) {
    }

    /** Persona ya registrada que coincide con los datos escritos (V9). openCaseId = caso abierto suyo en la sede consultada. */
    public record Match(UUID id, String fullName, String docType, String docNumber, String phone, String email, String status,
                        String branchName, UUID openCaseId) {
    }

    public record MatchResult(List<Match> people, boolean documentInOtherBranch) {
    }

    public record FunnelBranch(UUID branchId, String branchName, long newCases, long inFollowup, long integrated, long converted, long archived,
                               long total, Double integrationRate, Double conversionRate, Double avgFirstContactHours, long overdue) {
    }

    public record Funnel(List<FunnelBranch> branches, FunnelBranch totals) {
    }

    /** Formulario público. website es el señuelo (honeypot): si llega con texto, el envío se descarta sin avisar. */
    public record PublicRequest(String firstName, String lastName, String phone, String email, String branchCode, String howArrived,
                                String notes, Boolean consent, String website) {
    }

    public record PublicBranch(String code, String name) {
    }

    public record PublicOption(String code, String nameEs, String nameEn) {
    }

    public record PublicConfig(String organizationName, List<PublicBranch> branches, List<PublicOption> sources) {
    }
}
