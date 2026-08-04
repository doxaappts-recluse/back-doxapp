package pe.dcs.app.features.pastoral_followup;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.Membership;
import pe.dcs.app.entity.Organization;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.PersonBranch;
import pe.dcs.app.features.pastoral_followup.request.InactiveMemberFilterRequest;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.enums.StatusType;

import java.util.ArrayList;
import java.util.List;

/**
 * Personas cuya membresía VIGENTE (current=true) está en estado
 * INACTIVE — a diferencia de {@link pe.dcs.app.features.membership.MembershipSpecification}
 * (que trae a todos, con o sin membresía), acá el join a Membership
 * es INNER y fijo por status: solo aparece quien de verdad tiene un
 * período de membresía abierto marcado inactivo. Miembros Inactivos
 * es la pantalla propia de PASTORAL_FOLLOWUP (CRM Pastoral): ORG_ADMIN
 * ve su organización, cualquier otro rol su sede actual. SYSTEM queda
 * completamente fuera — ver PastoralFollowUpAccessGuard.
 */
public class InactiveMemberSpecification {

    private InactiveMemberSpecification() {
    }

    public static Specification<Person> filter(
            InactiveMemberFilterRequest filter,
            AuthContext authContext
    ) {

        InactiveMemberFilterRequest safeFilter =
                filter != null
                        ? filter
                        : new InactiveMemberFilterRequest();

        return (root, query, cb) -> {

            query.distinct(true);

            List<Predicate> predicates = new ArrayList<>();

            Join<Person, Membership> membership =
                    root.join("memberships", JoinType.INNER);

            predicates.add(cb.isTrue(membership.get("current")));
            predicates.add(cb.equal(membership.get("status"), StatusType.INACTIVE));

            /*
             * =====================================
             * SCOPE: SEDE / ORGANIZACION ACTUAL
             * =====================================
             */

            Join<Person, PersonBranch> personBranch =
                    root.join("branchHistory", JoinType.INNER);

            Join<PersonBranch, Branch> branch =
                    personBranch.join("branch", JoinType.INNER);

            predicates.add(
                    cb.equal(personBranch.get("status"), StatusType.ACTIVE)
            );

            if (authContext.isCurrentBranchAdmin()
                    && !authContext.isCurrentOrganizationAdmin()) {

                predicates.add(
                        cb.equal(branch.get("id"), authContext.getCurrentBranchId())
                );

            } else {

                Join<Branch, Organization> organization =
                        branch.join("organization", JoinType.INNER);

                predicates.add(
                        cb.equal(
                                organization.get("id"),
                                authContext.getCurrentOrganizationId()
                        )
                );
            }

            /*
             * =====================================
             * FILTROS
             * =====================================
             */

            if (safeFilter.getBranchId() != null) {

                Join<Person, PersonBranch> personBranchFilter =
                        root.join("branchHistory", JoinType.INNER);

                predicates.add(
                        cb.and(
                                cb.equal(personBranchFilter.get("status"), StatusType.ACTIVE),
                                cb.equal(personBranchFilter.get("branch").get("id"), safeFilter.getBranchId())
                        )
                );
            }

            if (hasText(safeFilter.getPersonName())) {

                String like = like(safeFilter.getPersonName());

                predicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("name")), like),
                                cb.like(cb.lower(root.get("lastname")), like)
                        )
                );
            }

            if (safeFilter.getHasAssignedLeader() != null) {

                predicates.add(
                        safeFilter.getHasAssignedLeader()
                                ? cb.isNotNull(root.get("assignedLeader"))
                                : cb.isNull(root.get("assignedLeader"))
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
