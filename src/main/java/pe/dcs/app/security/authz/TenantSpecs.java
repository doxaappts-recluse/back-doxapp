package pe.dcs.app.security.authz;

import org.springframework.data.jpa.domain.Specification;

/**
 * Núcleo 01 §2 · Specification de alcance obligatoria para consultas de tenant.
 * Uso: {@code repo.findAll(TenantSpecs.<Person>inScope(scope).and(otrosFiltros), pageable)}.
 * Un recurso fuera de alcance debe responder 404 (no revela existencia): usar {@code findOne(inScope(scope).and(idEquals))}.
 */
public final class TenantSpecs {

    private TenantSpecs() {
    }

    /** Solo organización (entidades sin sede). */
    public static <T> Specification<T> inOrganization(AccessScope scope) {
        return (root, query, cb) -> cb.equal(root.get("organizationId"), scope.organizationId());
    }

    /** Organización + sedes visibles (entidades con branchId). ORG_ADMIN ve todas las sedes de su organización. */
    public static <T> Specification<T> inScope(AccessScope scope) {
        return (root, query, cb) -> {
            var sameOrg = cb.equal(root.get("organizationId"), scope.organizationId());
            if (scope.allBranches()) {
                return sameOrg;
            }
            if (scope.branchIds().isEmpty()) {
                return cb.disjunction();
            }
            return cb.and(sameOrg, root.get("branchId").in(scope.branchIds()));
        };
    }

    public static <T> Specification<T> idEquals(java.util.UUID id) {
        return (root, query, cb) -> cb.equal(root.get("id"), id);
    }
}
