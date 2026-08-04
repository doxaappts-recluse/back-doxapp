package pe.dcs.app.features.branch_transfer;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.Organization;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.PersonBranch;
import pe.dcs.app.features.branch_transfer.request.BranchTransferFilterRequest;
import pe.dcs.app.features.branch_transfer.request.BranchTransferSearchRequest;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.enums.StatusType;

import java.util.ArrayList;
import java.util.List;

/**
 * Lista personas junto con su sede activa, para el listado
 * principal de "Traslados de Sede".
 *
 * Scope:
 * - SYSTEM: ve todas las personas.
 * - ORG_ADMIN: acotado a su organización.
 * - ORG_BRANCH_ADMIN: acotado a su sede (solo puede trasladar
 *   gente que ya está en su propia sede).
 */
public class BranchTransferSpecification {

    private BranchTransferSpecification() {
    }

    public static Specification<Person> filter(
            BranchTransferSearchRequest request,
            AuthContext authContext
    ) {

        return (root, query, cb) -> {

            query.distinct(true);

            List<Predicate> predicates = new ArrayList<>();

            BranchTransferFilterRequest filter =
                    request.getFilters() != null
                            ? request.getFilters()
                            : new BranchTransferFilterRequest();

            Join<Person, PersonBranch> personBranch =
                    root.join(
                            "branchHistory",
                            JoinType.INNER
                    );

            predicates.add(
                    cb.equal(
                            personBranch.get("status"),
                            StatusType.ACTIVE
                    )
            );

            Join<PersonBranch, Branch> branch =
                    personBranch.join(
                            "branch",
                            JoinType.INNER
                    );

            /*
             * =====================================
             * SCOPE: SEDE / ORGANIZACION ACTUAL
             * =====================================
             */

            if (!authContext.isSystem()) {

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
