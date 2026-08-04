package pe.dcs.app.features.inventory;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.InventoryAssignment;
import pe.dcs.app.features.inventory.request.InventoryAssignmentFilterRequest;
import pe.dcs.app.security.service.AuthContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Scope: org admin ve todas las asignaciones de su organización,
 * branch admin/org user delegado solo las de su sede actual (vía
 * item.branch). SYSTEM queda completamente fuera — ver
 * InventoryAccessGuard.
 */
public class InventoryAssignmentSpecification {

    private InventoryAssignmentSpecification() {
    }

    public static Specification<InventoryAssignment> filter(
            InventoryAssignmentFilterRequest filter,
            AuthContext authContext
    ) {

        InventoryAssignmentFilterRequest safeFilter =
                filter != null
                        ? filter
                        : new InventoryAssignmentFilterRequest();

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

            if (safeFilter.getItemId() != null) {
                predicates.add(
                        cb.equal(root.get("item").get("id"), safeFilter.getItemId())
                );
            }

            if (safeFilter.getAssignedToPersonId() != null) {
                predicates.add(
                        cb.equal(root.get("assignedToPerson").get("id"), safeFilter.getAssignedToPersonId())
                );
            }

            if (safeFilter.getAssignedToMinistryId() != null) {
                predicates.add(
                        cb.equal(root.get("assignedToMinistry").get("id"), safeFilter.getAssignedToMinistryId())
                );
            }

            if (Boolean.TRUE.equals(safeFilter.getActiveOnly())) {
                predicates.add(cb.isNull(root.get("returnedDate")));
            }

            return cb.and(
                    predicates.toArray(new Predicate[0])
            );
        };
    }
}
