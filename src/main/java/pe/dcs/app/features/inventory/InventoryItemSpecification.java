package pe.dcs.app.features.inventory;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.InventoryItem;
import pe.dcs.app.features.inventory.request.InventoryItemFilterRequest;
import pe.dcs.app.security.service.AuthContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Scope: org admin ve todos los ítems de su organización, branch
 * admin/org user delegado solo los de su sede actual. SYSTEM queda
 * completamente fuera — ver InventoryAccessGuard.
 */
public class InventoryItemSpecification {

    private InventoryItemSpecification() {
    }

    public static Specification<InventoryItem> filter(
            InventoryItemFilterRequest filter,
            AuthContext authContext
    ) {

        InventoryItemFilterRequest safeFilter =
                filter != null
                        ? filter
                        : new InventoryItemFilterRequest();

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

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

            if (hasText(safeFilter.getCategory())) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("category")),
                                like(safeFilter.getCategory())
                        )
                );
            }

            if (safeFilter.getStatus() != null) {
                predicates.add(
                        cb.equal(root.get("status"), safeFilter.getStatus())
                );
            }

            if (Boolean.TRUE.equals(safeFilter.getLowStockOnly())) {
                predicates.add(cb.isNotNull(root.get("minStock")));
                predicates.add(
                        cb.lessThanOrEqualTo(root.get("currentQuantity"), root.get("minStock"))
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
