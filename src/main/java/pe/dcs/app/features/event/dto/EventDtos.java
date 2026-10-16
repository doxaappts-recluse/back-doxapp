package pe.dcs.app.features.event.dto;

import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** M14 · Eventos (/admin/events), tarifas y preguntas, inscripciones (/admin/events/{id}/registrations) y formulario público. */
public final class EventDtos {

    private EventDtos() {
    }

    // ---------------------------------------------------------------- evento

    public record EventRequest(String name, String scope, UUID branchId, String typeCode, String description, Instant startAt, Instant endAt,
                               String location, String onlineUrl, String bannerUrl, Integer capacity, Boolean waitlistEnabled, Instant regOpensAt,
                               Instant regClosesAt, Instant cancelDeadline, Boolean isPublic, Boolean requiresApproval, Integer minAge, Integer guestsMax,
                               Boolean requirePaymentForCheckin, UUID fundId, UUID spaceId, Long version) {
    }

    public record EventSummary(UUID id, String name, String scope, UUID branchId, String branchName, String typeCode, Instant startAt, Instant endAt,
                               String location, String onlineUrl, Integer capacity, int registeredCount, int waitlistCount, String status, boolean isPublic,
                               Instant createdAt, Long version) {
    }

    public record PriceTier(String category, String amount, String currency) {
    }

    public record QuestionRequest(String label, String type, String options, Boolean required, Integer sortOrder) {
    }

    public record QuestionCreateRequest(UUID eventId, String label, String type, String options, Boolean required, Integer sortOrder) {
    }

    public record QuestionView(UUID id, String label, String type, String options, boolean required, int sortOrder) {
    }

    public record EventDetail(EventSummary summary, String description, String bannerUrl, boolean waitlistEnabled, Instant regOpensAt, Instant regClosesAt,
                              Instant cancelDeadline, boolean requiresApproval, Integer minAge, int guestsMax, boolean requirePaymentForCheckin,
                              String cancelReason, UUID fundId, String fundName, UUID spaceId, String spaceName, List<PriceTier> priceTiers,
                              List<QuestionView> questions) {
    }

    public record EventSearch(EventFilters filters, PaginationRequest pagination, List<SortRequest> sorts) {
        public record EventFilters(UUID branchId, String status, String scope, Instant from, Instant to) {
        }
    }

    // ---------------------------------------------------------------- inscripción

    public record RegisterRequest(UUID personId, String category, Integer guests, Map<String, String> answers, UUID guardianPersonId, String overrideReason) {
    }

    public record RegistrationSummary(UUID id, UUID eventId, String eventName, UUID personId, String personName, String category, String status,
                                      String paymentStatus, String amount, String currency, int guests, Instant createdAt, Long version) {
    }

    public record RegistrationDetail(RegistrationSummary summary, Map<String, String> answers, String ticketCode, Instant ticketUsedAt, String paymentMethod,
                                     String paymentReference, String cancelReason, String overrideReason, UUID guardianPersonId, String guardianName,
                                     UUID financialMovementId) {
    }

    public record RegistrationSearch(RegistrationFilters filters, PaginationRequest pagination, List<SortRequest> sorts) {
        public record RegistrationFilters(UUID eventId, UUID personId, String status, String paymentStatus) {
        }
    }

    public record PaymentRequest(String method, String reference) {
    }

    public record CheckinRequest(String ticketCode, UUID personId) {
    }

    public record CheckinResult(RegistrationSummary registration, String message) {
    }

    public record WaitlistPromoteResult(int promoted) {
    }

    public record CancelRequest(String reason, String refundReference) {
    }

    // ---------------------------------------------------------------- público (sin sesión)

    public record PublicOption(String code, String nameEs, String nameEn) {
    }

    public record PublicEventView(UUID id, String orgName, String name, String description, Instant startAt, Instant endAt, String location,
                                  String onlineUrl, String bannerUrl, List<PriceTier> priceTiers, List<QuestionView> questions, boolean full) {
    }

    public record PublicRegisterRequest(String firstName, String lastName, String phone, String email, String docType, String docNumber, Integer guests,
                                        Map<String, String> answers, Boolean consent, String website) {
    }
}
