package pe.dcs.app.features.space_reservation;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.ReservableSpace;
import pe.dcs.app.features.space_reservation.request.ReservableSpaceFilterRequest;
import pe.dcs.app.security.service.AuthContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Scope: org admin ve todos los espacios de su organización, branch
 * admin/org user delegado solo los de su sede actual. SYSTEM queda
 * completamente fuera — ver SpaceReservationAccessGuard.
 */
public class ReservableSpaceSpecification {

    private ReservableSpaceSpecification() {
    }

    public static Specification<ReservableSpace> filter(
            ReservableSpaceFilterRequest filter,
            AuthContext authContext
    ) {

        ReservableSpaceFilterRequest safeFilter =
                filter != null
                        ? filter
                        : new ReservableSpaceFilterRequest();

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (authContext.isCurrentOrganizationAdmin()) {

                predicates.add(
                        cb.equal(
                                root.get("branch").get("organization").get("id"),
                                authContext.getCurrentOrganizationId()
                        )
                );

            } else {

                predicates.add(
                        cb.equal(
                                root.get("branch").get("id"),
                                authContext.getCurrentBranchId()
                        )
                );
            }

            if (safeFilter.getBranchId() != null) {
                predicates.add(
                        cb.equal(root.get("branch").get("id"), safeFilter.getBranchId())
                );
            }

            if (hasText(safeFilter.getName())) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("name")),
                                like(safeFilter.getName())
                        )
                );
            }

            if (safeFilter.getStatus() != null) {
                predicates.add(
                        cb.equal(root.get("status"), safeFilter.getStatus())
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
