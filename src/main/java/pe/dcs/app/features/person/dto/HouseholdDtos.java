package pe.dcs.app.features.person.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import pe.dcs.app.features.organization.dto.AddressDto;
import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** M06 · Hogares (/admin/households). */
public final class HouseholdDtos {

    private HouseholdDtos() {
    }

    public record Search(Filters filters, PaginationRequest pagination, List<SortRequest> sorts) {
        public record Filters(String q, String status, UUID branchId) {
        }
    }

    public record MemberRequest(UUID personId, String role, Boolean guardian) {
    }

    /** Alta: nombre, dirección y los integrantes (con exactamente una cabeza). */
    public record Request(@Size(max = 100, message = "error.common.tooLong") String name, @Valid AddressDto address, List<MemberRequest> members, Long version) {
    }

    public record HeadRequest(UUID personId, String previousRole) {
    }

    public record Member(UUID personId, String fullName, String docNumber, Integer age, boolean minor, String status, UUID branchId,
                         String role, boolean guardian, LocalDate joinedAt, boolean hidden) {
    }

    public record Summary(UUID id, String name, String status, String headName, int members, int minors, String city) {
    }

    public record Response(UUID id, String name, AddressDto address, String status, List<Member> members, boolean minorWithoutGuardian,
                           Long version, Instant createdAt) {
    }
}
