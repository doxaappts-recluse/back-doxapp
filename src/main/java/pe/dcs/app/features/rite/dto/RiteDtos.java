package pe.dcs.app.features.rite.dto;

import pe.dcs.app.util.pagination.PaginationRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** M08 · Membresía y ritos (bautizo, matrimonio, presentación de niños) y sus certificados. */
public final class RiteDtos {

    private RiteDtos() {
    }

    // ---------------------------------------------------------------- reglas y requisitos
    public record Rules(int marriageMinAge, int dedicationMaxAge, Long version) {
    }

    public record RulesRequest(Integer marriageMinAge, Integer dedicationMaxAge, Long version) {
    }

    /** riteType: MEMBERSHIP | BAPTISM | MARRIAGE | DEDICATION; source: MANUAL | AGE (COURSE llega con M13). */
    public record RequirementRequest(String riteType, String code, String label, Boolean required, String source, Integer minAge, Boolean active, Integer sortOrder,
                                     Long version) {
    }

    public record RequirementResponse(UUID id, String riteType, String code, String label, boolean required, String source, Integer minAge, boolean active,
                                      int sortOrder, long version) {
    }

    /** met: cumple. auto: lo calcula el sistema (edad). detail: por qué no cumple, cuando es automático. */
    public record CheckItem(UUID requirementId, String code, String label, boolean required, String source, Integer minAge, boolean met, boolean auto, String note,
                            String detail) {
    }

    public record Checklist(List<CheckItem> items, boolean complete, boolean overridden, String overrideReason) {
    }

    public record CheckRequest(Boolean met, String note) {
    }

    public record OverrideRequest(String reason) {
    }

    // ---------------------------------------------------------------- membresía
    /** mode: REQUEST (por defecto: espera aprobación) | RECORD (registro directo con la acción A: queda activa de inmediato). */
    public record MembershipRequest(UUID personId, String kind, LocalDate startDate, String mode) {
    }

    public record MembershipUpdate(String kind, LocalDate startDate, Long version) {
    }

    public record MembershipSearch(Filters filters, PaginationRequest pagination) {
        public record Filters(String q, UUID branchId, String status, String kind) {
        }
    }

    public record EndRequest(String exitReason, String exitNotes, LocalDate endDate) {
    }

    public record MembershipResponse(UUID id, UUID personId, String personName, UUID branchId, String branchName, String kind, String status, boolean current,
                                     LocalDate startDate, LocalDate endDate, String exitReason, String exitNotes, boolean notesHidden, UUID approvalId, String origin,
                                     String overrideReason, String requestedByName, String cancelReason, boolean requirementsPending, boolean readOnly,
                                     Instant createdAt, long version) {
    }

    // ---------------------------------------------------------------- ritos
    /**
     * personId: bautizado, cónyuge 1 o niño. person2Id: cónyuge 2. guardianIds: tutores de la presentación. mode: REQUEST | RECORD (registro de un rito ya realizado).
     * En RECORD se exigen fecha (no futura) y oficiante.
     */
    public record RiteRequest(UUID personId, UUID person2Id, UUID officiantId, String officiantText, Boolean external, String externalChurch, UUID guardianConsentBy,
                              String civilRecordNo, List<UUID> guardianIds, LocalDate date, String place, String mode) {
    }

    public record RiteUpdate(UUID officiantId, String officiantText, Boolean external, String externalChurch, UUID guardianConsentBy, String civilRecordNo,
                             List<UUID> guardianIds, String place, Long version) {
    }

    public record RiteSearch(Filters filters, PaginationRequest pagination) {
        public record Filters(String q, UUID branchId, String status, UUID personId) {
        }
    }

    public record PersonRef(UUID id, String name) {
    }

    public record RiteResponse(UUID id, String riteType, UUID personId, String personName, UUID person2Id, String person2Name, UUID branchId, String branchName,
                               LocalDate date, String place, UUID officiantId, String officiantName, String officiantText, boolean external, String externalChurch,
                               UUID guardianConsentBy, String guardianConsentName, String civilRecordNo, Instant spouse2ConfirmedAt, String status, UUID approvalId,
                               String overrideReason, String origin, String requestedByName, String cancelReason, Instant completedAt, String certificateNo,
                               UUID certificateId, String certificateStatus, List<PersonRef> guardians, boolean requirementsPending, boolean readOnly,
                               Instant createdAt, long version) {
    }

    /** Programar: fecha desde hoy. */
    public record ScheduleRequest(LocalDate date, String place, UUID officiantId, String officiantText) {
    }

    /** Registrar la realización: fecha hasta hoy y oficiante (persona o texto). */
    public record CompleteRequest(LocalDate date, String place, UUID officiantId, String officiantText) {
    }

    public record ReasonRequest(String reason) {
    }

    // ---------------------------------------------------------------- certificados
    public record CertificateResponse(UUID id, String riteType, UUID riteId, String certificateNo, String code, String status, Instant issuedAt, String issuedByName,
                                      String voidReason, Instant voidedAt, Map<String, Object> snapshot, UUID issuedDocumentId) {
    }

    /** Lo que ve cualquiera que verifique un certificado: sin datos sensibles y con el nombre abreviado. */
    public record CertificateVerification(boolean found, String status, String riteType, String certificateNo, Instant issuedAt, String holder, String organization,
                                          String branch, LocalDate eventDate) {
    }

    // ---------------------------------------------------------------- línea de vida eclesial
    public record Timeline(UUID personId, String personName, List<MembershipResponse> memberships, List<RiteResponse> baptisms, List<RiteResponse> marriages,
                           List<RiteResponse> dedications, List<RiteResponse> guardianOf) {
    }
}
