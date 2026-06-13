package pe.dcs.app.features.user.shared;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import pe.dcs.app.entity.User;

import java.util.UUID;

public abstract class BaseUserSpecification {

    protected BaseUserSpecification() {
    }

    public  static Predicate baseFilters(
            Root<User> root,
            CriteriaBuilder cb,
            UUID organizationId,
            String name,
            String lastname
    ) {

        Predicate predicates = cb.conjunction();

        // organization
        predicates = cb.and(
                predicates,
                cb.equal(
                        root.get("organization").get("id"),
                        organizationId
                )
        );

        // name
        if (name != null && !name.isBlank()) {

            predicates = cb.and(
                    predicates,
                    cb.like(
                            cb.lower(root.get("name")),
                            "%" + name.toLowerCase() + "%"
                    )
            );
        }

        // lastname
        if (lastname != null && !lastname.isBlank()) {

            predicates = cb.and(
                    predicates,
                    cb.like(
                            cb.lower(root.get("lastname")),
                            "%" + lastname.toLowerCase() + "%"
                    )
            );
        }

        return predicates;
    }
}