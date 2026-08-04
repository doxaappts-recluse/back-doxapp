package pe.dcs.app.features.baptism;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.Baptism;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.Organization;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.PersonBranch;
import pe.dcs.app.features.baptism.request.BaptismFilterRequest;
import pe.dcs.app.features.baptism.request.BaptismSearchRequest;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.enums.StatusType;

import java.util.ArrayList;
import java.util.List;

/**
 * Lista personas junto con su bautizo (si tiene), para el
 * listado principal de "Bautizo".
 *
 * Scope:
 * - SYSTEM: ve todas las personas.
 * - ORG_ADMIN: acotado a su organización.
 * - ORG_BRANCH_ADMIN: acotado a su sede.
 */
public class BaptismSpecification {

    private BaptismSpecification() {
    }

    public static Specification<Person> filter(
            BaptismSearchRequest request,
            AuthContext authContext
    ) {

        return (root, query, cb) -> {

            query.distinct(true);

            List<Predicate> predicates = new ArrayList<>();

            BaptismFilterRequest filter =
                    request.getFilters() != null
                            ? request.getFilters()
                            : new BaptismFilterRequest();

            Join<Person, Baptism> baptism =
                    root.join(
                            "baptism",
                            JoinType.LEFT
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

                    /*
                     * Acotar a una sede puntual dentro de la
                     * organización — solo tiene sentido ofrecerlo a
                     * org admin (ver Reportes Avanzados), branch
                     * admin ya quedó fijado arriba.
                     */
                    if (filter.getBranchId() != null) {
                        predicates.add(
                                cb.equal(
                                        branch.get("id"),
                                        filter.getBranchId()
                                )
                        );
                    }
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
             * BAUTIZO: hasBaptism / verified
             * =====================================
             */

            if (filter.getHasBaptism() != null) {

                predicates.add(
                        filter.getHasBaptism()
                                ? cb.isNotNull(baptism.get("id"))
                                : cb.isNull(baptism.get("id"))
                );
            }

            if (filter.getVerified() != null) {

                predicates.add(
                        cb.equal(
                                baptism.get("verified"),
                                filter.getVerified()
                        )
                );
            }

            if (filter.getStartDate() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                baptism.get("baptismDate"),
                                filter.getStartDate()
                        )
                );
            }

            if (filter.getEndDate() != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(
                                baptism.get("baptismDate"),
                                filter.getEndDate()
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
