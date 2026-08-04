package pe.dcs.app.features.marriage;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.Marriage;
import pe.dcs.app.features.marriage.request.MarriageFilterRequest;
import pe.dcs.app.security.service.AuthContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Scope:
 * - SYSTEM: ve todo.
 * - ORG_ADMIN: acotado a toda su organización (todas las sedes).
 * - Cualquier otro rol (branch admin u org user delegado): acotado
 *   a su sede actual únicamente — mismo criterio que
 *   {@link pe.dcs.app.features.finance.FinancialMovementSpecification}.
 */
public class MarriageSpecification {

    private MarriageSpecification() {
    }

    public static Specification<Marriage> filter(
            MarriageFilterRequest filter,
            AuthContext authContext
    ) {

        MarriageFilterRequest safeFilter =
                filter != null
                        ? filter
                        : new MarriageFilterRequest();

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

            if (hasText(safeFilter.getSpouseName())) {

                String like = like(safeFilter.getSpouseName());

                predicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("spouse1Name")), like),
                                cb.like(cb.lower(root.get("spouse2Name")), like)
                        )
                );
            }

            if (hasText(safeFilter.getChurchName())) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("churchName")),
                                like(safeFilter.getChurchName())
                        )
                );
            }

            if (safeFilter.getStartDate() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("marriageDate"),
                                safeFilter.getStartDate()
                        )
                );
            }

            if (safeFilter.getEndDate() != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("marriageDate"),
                                safeFilter.getEndDate()
                        )
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
