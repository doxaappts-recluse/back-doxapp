package pe.dcs.app.features.inventory;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.InventoryMovement;
import pe.dcs.app.features.inventory.request.InventoryMovementFilterRequest;
import pe.dcs.app.security.service.AuthContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Scope: org admin ve todos los movimientos de su organización,
 * branch admin/org user delegado solo los de su sede actual (vía
 * item.branch). SYSTEM queda completamente fuera — ver
 * InventoryAccessGuard.
 */
public class InventoryMovementSpecification {

    private InventoryMovementSpecification() {
    }

    public static Specification<InventoryMovement> filter(
            InventoryMovementFilterRequest filter,
            AuthContext authContext
    ) {

        InventoryMovementFilterRequest safeFilter =
                filter != null
                        ? filter
                        : new InventoryMovementFilterRequest();

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (authContext.isCurrentOrganizationAdmin()) {

                predicates.add(
                        cb.equal(
                                root.get("item").get("branch").get("organization").get("id"),
                                authContext.getCurrentOrganizationId()
                        )
                );

            } else {

                predicates.add(
                        cb.equal(
                                root.get("item").get("branch").get("id"),
                                authContext.getCurrentBranchId()
                        )
                );
            }

            if (authContext.isCurrentOrganizationAdmin() && safeFilter.getBranchId() != null) {
                predicates.add(
                        cb.equal(
                                root.get("item").get("branch").get("id"),
                                safeFilter.getBranchId()
                        )
                );
            }

            if (safeFilter.getItemId() != null) {
                predicates.add(
                        cb.equal(root.get("item").get("id"), safeFilter.getItemId())
                );
            }

            if (safeFilter.getType() != null) {
                predicates.add(
                        cb.equal(root.get("type"), safeFilter.getType())
                );
            }

            if (safeFilter.getReason() != null) {
                predicates.add(
                        cb.equal(root.get("reason"), safeFilter.getReason())
                );
            }

            if (safeFilter.getFromDate() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(root.get("movementDate"), safeFilter.getFromDate())
                );
            }

            if (safeFilter.getToDate() != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(root.get("movementDate"), safeFilter.getToDate())
                );
            }

            return cb.and(
                    predicates.toArray(new Predicate[0])
            );
        };
    }
}
