package pe.dcs.app.features.ministry_role;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.MinistryRole;
import pe.dcs.app.util.enums.StatusType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MinistryRoleSpecification {

    public static Specification<MinistryRole> filter(
            UUID ministryId,
            String name,
            StatusType active
    ) {
        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            predicates.add(
                    cb.equal(
                            root.get("ministry").get("id"),
                            ministryId
                    )
            );

            /*if (name != null && !name.isBlank()) {
                predicates.add(
                        cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%")
                );
            }

            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }*/

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}