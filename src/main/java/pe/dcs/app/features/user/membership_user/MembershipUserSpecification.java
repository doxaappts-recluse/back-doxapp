package pe.dcs.app.features.user.membership_user;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.Membership;
import pe.dcs.app.entity.User;
import pe.dcs.app.features.user.shared.BaseUserSpecification;
import pe.dcs.app.util.enums.membership.MembershipStatus;

import java.util.UUID;

public class MembershipUserSpecification extends BaseUserSpecification {

    private MembershipUserSpecification() {
    }

    public static Specification<User> filter(
            UUID organizationId,
            String name,
            String lastname,
            Boolean hasMembership,
            String membershipStatus
    ) {

        return (root, query, cb) -> {

            query.distinct(true);

            Predicate predicates =
                    baseFilters(
                            root,
                            cb,
                            organizationId,
                            name,
                            lastname
                    );

            Join<User, Membership> membershipJoin =
                    root.join(
                            "memberships",
                            JoinType.LEFT
                    );

            // has membership
            if (hasMembership != null) {

                predicates = hasMembership
                        ? cb.and(
                        predicates,
                        cb.isNotNull(
                                membershipJoin.get("id")
                        )
                )
                        : cb.and(
                        predicates,
                        cb.isNull(
                                membershipJoin.get("id")
                        )
                );
            }

            // status
            if (membershipStatus != null
                    && !membershipStatus.isBlank()) {

                predicates = cb.and(
                        predicates,
                        cb.equal(
                                membershipJoin.get("status"),
                                MembershipStatus.valueOf(
                                        membershipStatus.toUpperCase()
                                )
                        )
                );
            }

            return predicates;
        };
    }
}