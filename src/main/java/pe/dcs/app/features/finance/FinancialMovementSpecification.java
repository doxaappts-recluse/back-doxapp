package pe.dcs.app.features.finance;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.FinancialMovement;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.enums.finance.FinancialMovementCategory;
import pe.dcs.app.util.enums.finance.FinancialMovementStatus;
import pe.dcs.app.util.enums.finance.FinancialMovementType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Scope:
 * - SYSTEM: ve todo.
 * - ORG_ADMIN: acotado a toda su organización (todas las sedes).
 * - Cualquier otro rol (branch admin u org user delegado): acotado
 *   a su sede actual únicamente — a diferencia de
 *   {@link pe.dcs.app.features.membership.MembershipSpecification},
 *   acá NO se trata como "org admin" por defecto a quien no es
 *   branch admin, porque un org user delegado sin rol admin no
 *   debe ver finanzas de sedes ajenas.
 */
public class FinancialMovementSpecification {

    private FinancialMovementSpecification() {
    }

    public static Specification<FinancialMovement> filter(
            AuthContext authContext,
            UUID branchId,
            FinancialMovementType type,
            FinancialMovementCategory category,
            FinancialMovementStatus status,
            UUID personId,
            UUID fundId,
            LocalDate startDate,
            LocalDate endDate,
            Boolean onlyAnonymous
    ) {

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
                                    root.get("organization").get("id"),
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

            if (branchId != null) {
                predicates.add(
                        cb.equal(root.get("branch").get("id"), branchId)
                );
            }

            if (type != null) {
                predicates.add(
                        cb.equal(root.get("type"), type)
                );
            }

            if (category != null) {
                predicates.add(
                        cb.equal(root.get("category"), category)
                );
            }

            if (status != null) {
                predicates.add(
                        cb.equal(root.get("status"), status)
                );
            }

            if (personId != null) {
                predicates.add(
                        cb.equal(root.get("person").get("id"), personId)
                );
            }

            if (Boolean.TRUE.equals(onlyAnonymous)) {
                predicates.add(cb.isNull(root.get("person")));
            }

            if (fundId != null) {
                predicates.add(
                        cb.equal(root.get("fund").get("id"), fundId)
                );
            }

            if (startDate != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("movementDate"),
                                startDate
                        )
                );
            }

            if (endDate != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("movementDate"),
                                endDate
                        )
                );
            }

            return cb.and(
                    predicates.toArray(new Predicate[0])
            );
        };
    }
}
