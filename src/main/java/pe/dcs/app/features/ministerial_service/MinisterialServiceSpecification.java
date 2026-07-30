package pe.dcs.app.features.ministerial_service;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.MinistryAssignment;
import pe.dcs.app.entity.Organization;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.PersonBranch;
import pe.dcs.app.features.ministerial_service.request.MinisterialServiceFilterRequest;
import pe.dcs.app.features.ministerial_service.request.MinisterialServiceSearchRequest;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.enums.StatusType;

import java.util.ArrayList;
import java.util.List;

/**
 * Lista personas junto con si sirven actualmente en algún
 * ministerio (al menos un MinistryAssignment con endDate=null),
 * para el listado principal de "Servicio Ministerial".
 *
 * Scope: SYSTEM ve todas, ORG_ADMIN acotado a su organización,
 * ORG_BRANCH_ADMIN acotado a su sede.
 */
public class MinisterialServiceSpecification {

    private MinisterialServiceSpecification() {
    }

    public static Specification<Person> filter(
            MinisterialServiceSearchRequest request,
            AuthContext authContext
    ) {

        return (root, query, cb) -> {

            query.distinct(true);

            List<Predicate> predicates = new ArrayList<>();

            MinisterialServiceFilterRequest filter =
                    request.getFilters() != null
                            ? request.getFilters()
                            : new MinisterialServiceFilterRequest();

            /*
             * =====================================
             * SERVICIO VIGENTE (LEFT JOIN restringido
             * a endDate IS NULL)
             * =====================================
             */

            Join<Person, MinistryAssignment> assignment =
                    root.join(
                            "ministryAssignments",
                            JoinType.LEFT
                    );

            assignment.on(
                    cb.isNull(assignment.get("endDate"))
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
             * HAS MINISTRY
             * =====================================
             */

            if (filter.getHasMinistry() != null) {

                predicates.add(
                        filter.getHasMinistry()
                                ? cb.isNotNull(assignment.get("id"))
                                : cb.isNull(assignment.get("id"))
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
