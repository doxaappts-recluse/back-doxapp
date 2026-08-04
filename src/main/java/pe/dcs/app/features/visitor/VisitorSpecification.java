package pe.dcs.app.features.visitor;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.Visitor;
import pe.dcs.app.security.service.AuthContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Visitantes forma parte del paquete comercial CRM Pastoral (junto a
 * Seguimiento Pastoral) — mismo criterio de scoping que
 * {@link pe.dcs.app.features.bible_academy.BibleClassSpecification}:
 * ORG_ADMIN acotado a su organización (todas las sedes), cualquier
 * otro rol acotado a su sede actual. SYSTEM queda completamente
 * fuera — ver VisitorAccessGuard.
 */
public class VisitorSpecification {

    private VisitorSpecification() {
    }

    public static Specification<Visitor> filter(
            VisitorFilterRequest filter,
            AuthContext authContext
    ) {

        VisitorFilterRequest safeFilter =
                filter != null
                        ? filter
                        : new VisitorFilterRequest();

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            Join<Visitor, Person> person = root.join("person");

            /*
             * =====================================
             * SCOPE: SEDE / ORGANIZACION ACTUAL
             * =====================================
             */

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

            if (hasText(safeFilter.getPersonName())) {

                String like = like(safeFilter.getPersonName());

                predicates.add(
                        cb.or(
                                cb.like(cb.lower(person.get("name")), like),
                                cb.like(cb.lower(person.get("lastname")), like),
                                cb.like(cb.lower(person.get("dni")), like)
                        )
                );
            }

            if (safeFilter.getHowArrived() != null) {
                predicates.add(cb.equal(root.get("howArrived"), safeFilter.getHowArrived()));
            }

            if (safeFilter.getConsolidationStage() != null) {
                predicates.add(cb.equal(root.get("consolidationStage"), safeFilter.getConsolidationStage()));
            }

            if (safeFilter.getStartDate() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("firstVisitDate"),
                                safeFilter.getStartDate()
                        )
                );
            }

            if (safeFilter.getEndDate() != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("firstVisitDate"),
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
