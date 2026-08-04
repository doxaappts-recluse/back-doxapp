package pe.dcs.app.features.visibility;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.VisibilityRequest;
import pe.dcs.app.features.visibility.request.VisibilityRequestFilterRequest;
import pe.dcs.app.features.visibility.request.VisibilityRequestSearchRequest;
import pe.dcs.app.security.service.AuthContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Solicitudes de visibilidad, en dos direcciones:
 * - INCOMING: solicitudes que otra sede hizo sobre la data DUEÑA
 *   de la sede actual (sourceBranch = sede actual) -> las revisa
 *   quien administra esa sede.
 * - OUTGOING: solicitudes que la sede actual hizo sobre data de
 *   otra sede (requestBranch = sede actual) -> para hacer
 *   seguimiento de lo que uno mismo pidió.
 *
 * SYSTEM/org admin ven todas las de su organización sin acotar
 * a una sede puntual.
 */
public class VisibilityRequestSpecification {

    public enum Direction {
        INCOMING,
        OUTGOING
    }

    private VisibilityRequestSpecification() {
    }

    public static Specification<VisibilityRequest> filter(
            VisibilityRequestSearchRequest request,
            AuthContext authContext,
            Direction direction
    ) {

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            VisibilityRequestFilterRequest filter =
                    request.getFilters() != null
                            ? request.getFilters()
                            : new VisibilityRequestFilterRequest();

            String branchField =
                    direction == Direction.INCOMING
                            ? "sourceBranch"
                            : "requestBranch";

            Join<VisibilityRequest, Branch> branch =
                    root.join(branchField, JoinType.INNER);

            if (authContext.isSystem()) {

                // Sin acotar organización.

            } else if (authContext.isCurrentOrganizationAdmin()) {

                predicates.add(
                        cb.equal(
                                branch.get("organization").get("id"),
                                authContext.getCurrentOrganizationId()
                        )
                );

            } else {

                predicates.add(
                        cb.equal(
                                branch.get("id"),
                                authContext.getCurrentBranchId()
                        )
                );
            }

            if (filter.getStatus() != null) {

                predicates.add(
                        cb.equal(
                                root.get("status"),
                                filter.getStatus()
                        )
                );
            }

            if (filter.getModuleCode() != null && !filter.getModuleCode().isBlank()) {

                predicates.add(
                        cb.equal(
                                root.get("module").get("code"),
                                filter.getModuleCode()
                        )
                );
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
