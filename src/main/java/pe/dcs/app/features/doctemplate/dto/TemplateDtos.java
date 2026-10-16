package pe.dcs.app.features.doctemplate.dto;

import pe.dcs.app.util.pagination.PaginationRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** M18 · DTOs de plantillas base (N1), plantillas de la organización, firmantes y documentos emitidos. */
public final class TemplateDtos {

    private TemplateDtos() {
    }

    // ---------------------------------------------------------------- plantillas base (N1)
    public record BaseTemplateRequest(String type, String name, Map<String, Object> design, String locale) {
    }

    public record BaseTemplateView(UUID id, String type, String name, Map<String, Object> design, String locale, int version, String status,
                                   Instant createdAt, Instant updatedAt) {
    }

    // ---------------------------------------------------------------- plantillas de la organización (N2)
    public record TemplateRequest(UUID branchId, String type, String name, Map<String, Object> design, String locale, Long version) {
    }

    public record TemplateView(UUID id, UUID branchId, String branchName, UUID baseTemplateId, String type, String name, Map<String, Object> design,
                               String locale, int templateVersion, String status, boolean isDefault, Instant createdAt, Instant updatedAt, long version) {
    }

    public record TemplateSearch(TemplateFilters filters, PaginationRequest pagination) {
        public record TemplateFilters(UUID branchId, String type, String status) {
        }
    }

    public record PreviewRequest(Map<String, Object> design, String locale) {
    }

    public record PreviewResult(boolean ok, String pdfBase64, String error) {
    }

    public record VariableCatalog(List<String> common, List<String> specific, List<String> mandatory) {
    }

    public record ImageUploadResult(String key, String contentType, long size) {
    }

    // ---------------------------------------------------------------- firmantes
    public record SignatoryRequest(UUID personId, String title, Long version) {
    }

    public record SignatoryView(UUID id, UUID personId, String personName, String title, boolean hasSignature, boolean active, Instant createdAt, long version) {
    }

    // ---------------------------------------------------------------- documentos emitidos
    public record IssueRequest(UUID branchId, String type, String subjectType, UUID subjectId, Map<String, String> variables, UUID signatoryId) {
    }

    public record IssuedView(UUID id, UUID branchId, String branchName, String type, UUID templateId, int templateVersion, String subjectType, UUID subjectId,
                             String documentNo, String verificationCode, String status, String voidReason, boolean isDuplicate, Instant issuedAt,
                             UUID issuedBy, String issuedByName) {
    }

    public record IssuedSearch(IssuedFilters filters, PaginationRequest pagination) {
        public record IssuedFilters(UUID branchId, String type, String status, UUID subjectId) {
        }
    }

    public record VoidRequest(String reason) {
    }

    public record VerificationResult(boolean found, String status, String type, String documentNo, Instant issuedAt, String holder, String organization,
                                     String branch) {
    }
}
