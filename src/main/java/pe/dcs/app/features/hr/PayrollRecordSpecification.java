package pe.dcs.app.features.hr;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.PayrollRecord;
import pe.dcs.app.features.hr.request.PayrollRecordFilterRequest;
import pe.dcs.app.security.service.AuthContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Scope: org admin ve todos los pagos de su organización, branch
 * admin/org user delegado solo los de su sede actual (vía
 * staff.branch). SYSTEM queda completamente fuera — ver
 * HrAccessGuard.
 */
public class PayrollRecordSpecification {

    private PayrollRecordSpecification() {
    }

    public static Specification<PayrollRecord> filter(
            PayrollRecordFilterRequest filter,
            AuthContext authContext
    ) {

        PayrollRecordFilterRequest safeFilter =
                filter != null
                        ? filter
                        : new PayrollRecordFilterRequest();

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

            if (safeFilter.getStaffId() != null) {
                predicates.add(
                        cb.equal(root.get("staff").get("id"), safeFilter.getStaffId())
                );
            }

            if (safeFilter.getPeriodMonth() != null) {
                predicates.add(
                        cb.equal(root.get("periodMonth"), safeFilter.getPeriodMonth())
                );
            }

            if (safeFilter.getPeriodYear() != null) {
                predicates.add(
                        cb.equal(root.get("periodYear"), safeFilter.getPeriodYear())
                );
            }

            if (safeFilter.getFromDate() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(root.get("paymentDate"), safeFilter.getFromDate())
                );
            }

            if (safeFilter.getToDate() != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(root.get("paymentDate"), safeFilter.getToDate())
                );
            }

            return cb.and(
                    predicates.toArray(new Predicate[0])
            );
        };
    }
}
