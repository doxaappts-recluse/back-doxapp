package pe.dcs.app.features.smallgroup;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.SmallGroup;
import pe.dcs.app.features.smallgroup.request.SmallGroupFilterRequest;
import pe.dcs.app.security.service.AuthContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Scope:
 * - SYSTEM: ve todo.
 * - ORG_ADMIN: acotado a toda su organización (todas las sedes).
 * - Branch admin u org user delegado: acotado a su sede actual
 *   únicamente — mismo criterio que
 *   {@link pe.dcs.app.features.marriage.MarriageSpecification}.
 */
public class SmallGroupSpecification {

    private SmallGroupSpecification() {
    }

    public static Specification<SmallGroup> filter(
            SmallGroupFilterRequest filter,
            AuthContext authContext
    ) {

        SmallGroupFilterRequest safeFilter =
                filter != null
                        ? filter
                        : new SmallGroupFilterRequest();

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            /*
             * =====================================
             * SCOPE: SEDE / ORGANIZACION ACTUAL
             * =====================================
             */

            if (!authContext.isSystem()) {

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
            }

            /*
             * =====================================
             * FILTROS
             * =====================================
             */

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

            if (hasText(safeFilter.getCategory())) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("category")),
                                like(safeFilter.getCategory())
                        )
                );
            }

            if (hasText(safeFilter.getLeaderName())) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("leaderName")),
                                like(safeFilter.getLeaderName())
                        )
                );
            }

            if (safeFilter.getStatus() != null) {
                predicates.add(
                        cb.equal(root.get("status"), safeFilter.getStatus())
                );
            }

            if (safeFilter.getStartDate() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(root.get("startDate"), safeFilter.getStartDate())
                );
            }

            if (safeFilter.getEndDate() != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(root.get("startDate"), safeFilter.getEndDate())
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
