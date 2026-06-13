package pe.dcs.app.features.user.ministry_user;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.MemberMinistryAssignment;
import pe.dcs.app.entity.User;
import pe.dcs.app.features.user.shared.BaseUserSpecification;

import java.util.UUID;

public class MinistryUserSpecification {

    private MinistryUserSpecification() {
    }

    public static Specification<User> filter(
            UUID organizationId,
            String name,
            String lastname,
            Boolean hasMinistry
    ) {

        return (root, query, cb) -> {

            Predicate p = BaseUserSpecification.baseFilters(
                    root,
                    cb,
                    organizationId,
                    name,
                    lastname
            );

            if (hasMinistry == null) {
                return p;
            }

            Subquery<Long> subquery = query.subquery(Long.class);
            Root<MemberMinistryAssignment> mma = subquery.from(MemberMinistryAssignment.class);

            subquery.select(cb.literal(1L));
            subquery.where(
                    cb.equal(mma.get("user"), root)
            );

            p = cb.and(
                    p,
                    hasMinistry
                            ? cb.exists(subquery)
                            : cb.not(cb.exists(subquery))
            );

            return p;
        };
    }
}