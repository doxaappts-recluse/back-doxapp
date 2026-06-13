package pe.dcs.app.features.organization;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.Organization;
import pe.dcs.app.features.organization.request.OrganizationListRequest;

import java.util.ArrayList;
import java.util.List;

public class OrganizationSpecification {

    private OrganizationSpecification() {}

    public static Specification<Organization> filter(
            OrganizationListRequest request
    ) {

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (request == null || request.getFilters() == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            var f = request.getFilters();

            // =========================
            // NAME
            // =========================
            if (f.getName() != null && !f.getName().isBlank()) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("name")),
                                "%" + f.getName().toLowerCase() + "%"
                        )
                );
            }

            // =========================
            // ADDRESS
            // =========================
            if (f.getAddress() != null && !f.getAddress().isBlank()) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("address")),
                                "%" + f.getAddress().toLowerCase() + "%"
                        )
                );
            }

            // =========================
            // ADDRESS
            // =========================
            if (f.getRuc() != null && !f.getRuc().isBlank()) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("ruc")),
                                "%" + f.getRuc().toLowerCase() + "%"
                        )
                );
            }

            // =========================
            // STATUS
            // =========================
            if (f.getStatus() != null) {
                predicates.add(
                        cb.equal(
                                root.get("status"),
                                f.getStatus()
                        )
                );
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}