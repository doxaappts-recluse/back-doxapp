package pe.dcs.app.features.person.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import pe.dcs.app.features.organization.dto.AddressDto;
import pe.dcs.app.shared.vo.DocumentType;
import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** M06 · Contratos de la API de personas (/admin/persons). */
public final class PersonDtos {

    private PersonDtos() {
    }

    /** Filtros: q (nombre, documento, correo, teléfono), sede, estado, etiqueta, edad, menores y hogar (WITH | WITHOUT). */
    public record Search(Filters filters, PaginationRequest pagination, List<SortRequest> sorts) {
        public record Filters(String q, UUID branchId, String status, UUID tagId, Integer ageFrom, Integer ageTo, Boolean minor, String household, Boolean anonymized) {
        }
    }

    /** Alta y edición. En la edición, {@code documentChangeReason} es obligatorio si cambia el documento [V7]. */
    public record Request(
            DocumentType docType,
            @Size(max = 12, message = "error.common.tooLong") String docNumber,
            @Size(max = 80, message = "error.common.tooLong") String firstName,
            @Size(max = 80, message = "error.common.tooLong") String lastName,
            String sex,
            LocalDate birthDate,
            String maritalStatus,
            @Size(max = 160, message = "error.common.tooLong") String email,
            @Size(max = 30, message = "error.common.tooLong") String phone,
            @Size(max = 30, message = "error.common.tooLong") String whatsapp,
            @Valid AddressDto address,
            @Size(max = 120, message = "error.common.tooLong") String occupation,
            UUID primaryBranchId,
            LocalDate joinedAt,
            @Size(max = 2000, message = "error.common.tooLong") String privateNotes,
            @Size(max = 2000, message = "error.common.tooLong") String allergies,
            @Size(max = 300, message = "error.common.tooLong") String documentChangeReason,
            Long version,
            Boolean consentGranted) {
    }

    public record StatusRequest(String status, @Size(max = 300, message = "error.common.tooLong") String reason, LocalDate date, Long version) {
    }

    public record BranchRequest(UUID branchId, @Size(max = 300, message = "error.common.tooLong") String reason, Long version) {
    }

    public record TagsRequest(List<UUID> tagIds) {
    }

    public record BranchRef(UUID id, String name, String code) {
    }

    public record TagRef(UUID id, String name, String color) {
    }

    public record HouseholdRef(UUID id, String name, String role, boolean guardian) {
    }

    /** Fila de la lista. */
    public record Summary(UUID id, String fullName, String firstName, String lastName, String docType, String docNumber, Integer age,
                          Boolean minor, String sex, String phone, String email, String status, UUID branchId, String branchName,
                          String householdName, List<TagRef> tags) {
    }

    /** Ficha completa. Sin la acción H, {@code privateNotes} y {@code allergies} viajan en null y {@code sensitiveMasked} en true. */
    public record Response(UUID id, String docType, String docNumber, String firstName, String lastName, String fullName, String sex,
                           LocalDate birthDate, Integer age, boolean minor, String maritalStatus, String email, String phone,
                           String whatsapp, AddressDto address, String occupation, BranchRef primaryBranch, LocalDate joinedAt,
                           String status, String statusReason, LocalDate deceasedAt, UUID mergedInto, String privateNotes, String allergies,
                           boolean sensitiveMasked, boolean hasSensitive, boolean hasAccess, List<TagRef> tags, HouseholdRef household,
                           Long version, Instant createdAt, Instant updatedAt, boolean hasPhoto, Instant photoUpdatedAt, boolean anonymized,
                           boolean readOnly) {
    }

    /** Estado de una finalidad de consentimiento; {@code granted} = hay un consentimiento vigente. */
    public record ConsentPurpose(String code, boolean granted, Instant grantedAt, String version, String source) {
    }

    /** Un consentimiento otorgado (y, si se revocó, cuándo). */
    public record ConsentRecord(String purpose, String version, Instant grantedAt, String source, Instant revokedAt) {
    }

    public record Consents(List<ConsentPurpose> purposes, List<ConsentRecord> history) {
    }

    public record ConsentRequest(boolean granted) {
    }

    public record Photo(boolean hasPhoto, Instant photoUpdatedAt) {
    }

    /** Resultado de buscar por documento antes de crear. Fuera del alcance: sin datos personales [V16]. */
    public record Lookup(boolean exists, boolean visible, boolean existsOtherBranch, UUID personId, String fullName, String status,
                         String branchName) {
    }

    /** Elemento mínimo del selector de personas (PersonPicker). */
    public record Picker(UUID id, String fullName, String docType, String docNumber, String status, Integer age, String branchName) {
    }

    public record BranchPeriod(UUID branchId, String branchName, LocalDate from, LocalDate to, boolean current, String reason) {
    }

    public record TimelineItem(Instant at, String action, String actorName, String actorRole, List<String> fields, String detail) {
    }

    public record History(List<BranchPeriod> branches, List<TimelineItem> timeline) {
    }

    // ---------------------------------------------------------------- fusión

    /** Persona que participa en una fusión. */
    public record MergeSide(UUID id, String fullName, String docType, String docNumber, String status, String branchName, boolean hasAccess,
                            boolean hasPhoto, long version) {
    }

    /** Un campo comparable: valores de ambas personas, si difieren y cuál se propone conservar (SOURCE | TARGET). */
    public record MergeField(String field, String source, String target, boolean differs, String proposed) {
    }

    public record MergeImpact(String key, int count) {
    }

    public record MergeBlocker(String code, String message) {
    }

    /** Vista previa: {@code source} es la persona duplicada (quedará como fusionada) y {@code target} la que se conserva. */
    public record MergePreview(MergeSide source, MergeSide target, List<MergeField> fields, List<MergeImpact> impact, List<MergeBlocker> blockers) {
    }

    /** {@code choices}: campo → SOURCE | TARGET (lo no indicado usa lo propuesto). Las versiones evitan pisar cambios de otra persona. */
    public record MergeRequest(UUID targetId, java.util.Map<String, String> choices, Long sourceVersion, Long targetVersion) {
    }

    // ---------------------------------------------------------------- anonimización

    public record AnonymizeRequest(@Size(max = 200, message = "error.common.tooLong") String confirmation, Long version) {
    }

    // ---------------------------------------------------------------- importación

    public record ImportRowView(int row, String action, String document, String name, String message) {
    }

    /** Resultado de validar (o de confirmar) un archivo: totales y una muestra de filas. {@code action}: CREATE | UPDATE | SKIP | ERROR. */
    public record ImportSummary(UUID jobId, String status, String fileName, String onDuplicate, int total, int create, int update, int skip,
                                int error, List<ImportRowView> sample, boolean sampleTruncated) {
    }
}
