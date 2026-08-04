package pe.dcs.app.features.familygroup;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.FamilyGroup;
import pe.dcs.app.entity.FamilyMember;
import pe.dcs.app.features.familygroup.request.FamilyGroupFilterRequest;
import pe.dcs.app.security.service.AuthContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Scope:
 * - SYSTEM: ve todo.
 * - ORG_ADMIN: acotado a toda su organización (todas las sedes).
 * - Branch admin u org user delegado: acotado a su sede actual
 *   únicamente — mismo criterio que
 *   {@link pe.dcs.app.features.smallgroup.SmallGroupSpecification}.
 */
public class FamilyGroupSpecification {

    private FamilyGroupSpecification() {
    }

    public static Specification<FamilyGroup> filter(
            FamilyGroupFilterRequest filter,
            AuthContext authContext
    ) {

        FamilyGroupFilterRequest safeFilter =
                filter != null
                        ? filter
                        : new FamilyGroupFilterRequest();

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

            if (safeFilter.getStatus() != null) {
                predicates.add(
                        cb.equal(root.get("status"), safeFilter.getStatus())
                );
            }

            if (hasText(safeFilter.getMemberDni())) {

                Join<FamilyGroup, FamilyMember> members =
                        root.join("members", JoinType.LEFT);

                query.distinct(true);

                predicates.add(
                        cb.like(
                                cb.lower(members.get("person").get("dni")),
                                like(safeFilter.getMemberDni())
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
