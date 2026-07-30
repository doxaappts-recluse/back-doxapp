package pe.dcs.app.features.membership;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.Membership;
import pe.dcs.app.entity.Organization;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.PersonBranch;
import pe.dcs.app.features.membership.request.MembershipFilterRequest;
import pe.dcs.app.features.membership.request.MembershipSearchRequest;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.enums.StatusType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lista personas junto con su membresía vigente (current=true),
 * para el listado principal de "Membresía".
 *
 * Scope:
 * - SYSTEM: ve todas las personas.
 * - ORG_ADMIN: acotado a su organización.
 * - ORG_BRANCH_ADMIN: acotado a su sede.
 */
public class MembershipSpecification {

    private MembershipSpecification() {
    }

    public static Specification<Person> filter(
            MembershipSearchRequest request,
            AuthContext authContext
    ) {

        return (root, query, cb) -> {

            query.distinct(true);

            List<Predicate> predicates = new ArrayList<>();

            MembershipFilterRequest filter =
                    request.getFilters() != null
                            ? request.getFilters()
                            : new MembershipFilterRequest();

            /*
             * =====================================
             * MEMBRESIA VIGENTE (LEFT JOIN restringido
             * a current = true, para no duplicar filas
             * por historial)
             * =====================================
             */

            Join<Person, Membership> membership =
                    root.join(
                            "memberships",
                            JoinType.LEFT
                    );

            membership.on(
                    cb.isTrue(membership.get("current"))
            );

            /*
             * =====================================
             * SCOPE: SEDE / ORGANIZACION ACTUAL
             * =====================================
             */

            if (!authContext.isSystem()) {

                Join<Person, PersonBranch> personBranch =
                        root.join(
                                "branchHistory",
                                JoinType.INNER
                        );

                Join<PersonBranch, Branch> branch =
                        personBranch.join(
                                "branch",
                                JoinType.INNER
                        );

                predicates.add(
                        cb.equal(
                                personBranch.get("status"),
                                StatusType.ACTIVE
                        )
                );

                if (authContext.isCurrentBranchAdmin()
                        && !authContext.isCurrentOrganizationAdmin()) {

                    predicates.add(
                            cb.equal(
                                    branch.get("id"),
                                    authContext.getCurrentBranchId()
                            )
                    );

                } else {

                    Join<Branch, Organization> organization =
                            branch.join(
                                    "organization",
                                    JoinType.INNER
                            );

                    predicates.add(
                            cb.equal(
                                    organization.get("id"),
                                    authContext.getCurrentOrganizationId()
                            )
                    );
                }
            }

            /*
             * =====================================
             * DATOS PERSONA
             * =====================================
             */

            if (hasText(filter.getName())) {

                predicates.add(
                        cb.like(
                                cb.lower(root.get("name")),
                                like(filter.getName())
                        )
                );
            }

            if (hasText(filter.getLastname())) {

                predicates.add(
                        cb.like(
                                cb.lower(root.get("lastname")),
                                like(filter.getLastname())
                        )
                );
            }

            /*
             * =====================================
             * MEMBRESIA: hasMembership / status
             * =====================================
             */

            if (filter.getHasMembership() != null) {

                predicates.add(
                        filter.getHasMembership()
                                ? cb.isNotNull(membership.get("id"))
                                : cb.isNull(membership.get("id"))
                );
            }

            if (filter.getMembershipStatus() != null) {

                predicates.add(
                        cb.equal(
                                membership.get("status"),
                                filter.getMembershipStatus()
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
