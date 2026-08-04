package pe.dcs.app.features.hr;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.LeaveRequest;
import pe.dcs.app.features.hr.request.LeaveRequestFilterRequest;
import pe.dcs.app.security.service.AuthContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Scope: org admin ve todas las solicitudes de su organización,
 * branch admin/org user delegado solo las de su sede actual (vía
 * staff.branch). SYSTEM queda completamente fuera — ver
 * HrAccessGuard.
 */
public class LeaveRequestSpecification {

    private LeaveRequestSpecification() {
    }

    public static Specification<LeaveRequest> filter(
            LeaveRequestFilterRequest filter,
            AuthContext authContext
    ) {

        LeaveRequestFilterRequest safeFilter =
                filter != null
                        ? filter
                        : new LeaveRequestFilterRequest();

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (authContext.isCurrentOrganizationAdmin()) {

                predicates.add(
                        cb.equal(
                                root.get("staff").get("branch").get("organization").get("id"),
                                authContext.getCurrentOrganizationId()
                        )
                );

            } else {

                predicates.add(
                        cb.equal(
                                root.get("staff").get("branch").get("id"),
                                authContext.getCurrentBranchId()
                        )
                );
            }

            if (authContext.isCurrentOrganizationAdmin() && safeFilter.getBranchId() != null) {
                predicates.add(
                        cb.equal(
                                root.get("staff").get("branch").get("id"),
                                safeFilter.getBranchId()
                        )
                );
            }

            if (safeFilter.getStaffId() != null) {
                predicates.add(
                        cb.equal(root.get("staff").get("id"), safeFilter.getStaffId())
                );
            }

            if (safeFilter.getType() != null) {
                predicates.add(
                        cb.equal(root.get("type"), safeFilter.getType())
                );
            }

            if (safeFilter.getStatus() != null) {
                predicates.add(
                        cb.equal(root.get("status"), safeFilter.getStatus())
                );
            }

            if (safeFilter.getFromDate() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(root.get("startDate"), safeFilter.getFromDate())
                );
            }

            if (safeFilter.getToDate() != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(root.get("endDate"), safeFilter.getToDate())
                );
            }

            return cb.and(
                    predicates.toArray(new Predicate[0])
            );
        };
    }
}
