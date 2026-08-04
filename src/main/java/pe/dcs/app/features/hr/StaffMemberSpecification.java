package pe.dcs.app.features.hr;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.StaffMember;
import pe.dcs.app.features.hr.request.StaffMemberFilterRequest;
import pe.dcs.app.security.service.AuthContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Scope: org admin ve todas las fichas de su organización, branch
 * admin/org user delegado solo las de su sede actual. SYSTEM queda
 * completamente fuera — ver HrAccessGuard.
 */
public class StaffMemberSpecification {

    private StaffMemberSpecification() {
    }

    public static Specification<StaffMember> filter(
            StaffMemberFilterRequest filter,
            AuthContext authContext
    ) {

        StaffMemberFilterRequest safeFilter =
                filter != null
                        ? filter
                        : new StaffMemberFilterRequest();

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

            if (hasText(safeFilter.getSearch())) {

                String search = like(safeFilter.getSearch());

                predicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("person").get("name")), search),
                                cb.like(cb.lower(root.get("person").get("lastname")), search),
                                cb.like(cb.lower(root.get("person").get("dni")), search)
                        )
                );
            }

            if (hasText(safeFilter.getPosition())) {
                predicates.add(
                        cb.like(cb.lower(root.get("position")), like(safeFilter.getPosition()))
                );
            }

            if (safeFilter.getContractType() != null) {
                predicates.add(
                        cb.equal(root.get("contractType"), safeFilter.getContractType())
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
