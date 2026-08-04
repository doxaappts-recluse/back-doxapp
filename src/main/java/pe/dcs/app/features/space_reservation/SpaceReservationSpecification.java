package pe.dcs.app.features.space_reservation;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.SpaceReservation;
import pe.dcs.app.features.space_reservation.request.SpaceReservationFilterRequest;
import pe.dcs.app.security.service.AuthContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Scope: org admin ve todas las reservas de su organización, branch
 * admin/org user delegado solo las de su sede actual (vía
 * space.branch). SYSTEM queda completamente fuera — ver
 * SpaceReservationAccessGuard.
 */
public class SpaceReservationSpecification {

    private SpaceReservationSpecification() {
    }

    public static Specification<SpaceReservation> filter(
            SpaceReservationFilterRequest filter,
            AuthContext authContext
    ) {

        SpaceReservationFilterRequest safeFilter =
                filter != null
                        ? filter
                        : new SpaceReservationFilterRequest();

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (authContext.isCurrentOrganizationAdmin()) {

                predicates.add(
                        cb.equal(
                                root.get("space").get("branch").get("organization").get("id"),
                                authContext.getCurrentOrganizationId()
                        )
                );

            } else {

                predicates.add(
                        cb.equal(
                                root.get("space").get("branch").get("id"),
                                authContext.getCurrentBranchId()
                        )
                );
            }

            if (authContext.isCurrentOrganizationAdmin() && safeFilter.getBranchId() != null) {
                predicates.add(
                        cb.equal(
                                root.get("space").get("branch").get("id"),
                                safeFilter.getBranchId()
                        )
                );
            }

            if (safeFilter.getSpaceId() != null) {
                predicates.add(
                        cb.equal(root.get("space").get("id"), safeFilter.getSpaceId())
                );
            }

            if (hasText(safeFilter.getPurpose())) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("purpose")),
                                like(safeFilter.getPurpose())
                        )
                );
            }

            if (safeFilter.getSourceType() != null) {
                predicates.add(
                        cb.equal(root.get("sourceType"), safeFilter.getSourceType())
                );
            }

            if (safeFilter.getStatus() != null) {
                predicates.add(
                        cb.equal(root.get("status"), safeFilter.getStatus())
                );
            }

            if (safeFilter.getFromDateTime() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(root.get("startDateTime"), safeFilter.getFromDateTime())
                );
            }

            if (safeFilter.getToDateTime() != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(root.get("endDateTime"), safeFilter.getToDateTime())
                );
            }

            return cb.and(
                    predicates.toArray(new Predicate[0])
            );
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String like(String value) {
        return "%" + value.toLowerCase() + "%";
    }
}
