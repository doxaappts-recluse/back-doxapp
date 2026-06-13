package pe.dcs.app.features.user.org_user;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.User;
import pe.dcs.app.features.user.shared.BaseUserSpecification;

import java.util.UUID;

public class OrgUserSpecification extends BaseUserSpecification {

    public static Specification<User> filter(
            UUID organizationId,
            String name,
            String lastname,
            String sex,
            String dni
    ) {
        return (root, query, cb) -> {

            Predicate predicates = baseFilters(
                    root,
                    cb,
                    organizationId,
                    name,
                    lastname
            );

            if (sex != null && !sex.isBlank()) {
                predicates = cb.and(
                        predicates,
                        cb.equal(cb.lower(root.get("sex")), sex.toLowerCase())
                );
            }

            if (dni != null && !dni.isBlank()) {
                predicates = cb.and(
                        predicates,
                        cb.equal(cb.lower(root.get("dni")), dni.toLowerCase())
                );
            }

            return predicates;
        };
    }
}