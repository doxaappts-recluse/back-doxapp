package pe.dcs.app.features.user.baptism_user;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.Baptism;
import pe.dcs.app.entity.User;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BaptismSpecification {

    private BaptismSpecification() {
    }

    public static Specification<Baptism> filter(
            UUID organizationId,
            String name,
            String lastname,
            Boolean verified
    ) {

        return (root, query, cb) -> {

            Join<Baptism, User> user =
                    root.join("user");

            List<Predicate> predicates =
                    new ArrayList<>();

            predicates.add(
                    cb.equal(
                            user.get("organization").get("id"),
                            organizationId
                    )
            );

            if (name != null && !name.isBlank()) {

                predicates.add(
                        cb.like(
                                cb.lower(user.get("name")),
                                "%" + name.toLowerCase() + "%"
                        )
                );
            }

            if (lastname != null && !lastname.isBlank()) {

                predicates.add(
                        cb.like(
                                cb.lower(user.get("lastname")),
                                "%" + lastname.toLowerCase() + "%"
                        )
                );
            }

            if (verified != null) {

                predicates.add(
                        cb.equal(
                                root.get("verified"),
                                verified
                        )
                );
            }

            return cb.and(
                    predicates.toArray(new Predicate[0])
            );
        };
    }

}