package pe.dcs.app.features.user.access_user;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.Credential;
import pe.dcs.app.entity.User;
import pe.dcs.app.features.user.shared.BaseUserSpecification;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

public class AccessUserSpecification
        extends BaseUserSpecification {

    private AccessUserSpecification() {
    }

    public static Specification<User> filter(
            UUID organizationId,
            String name,
            String lastname,
            Boolean hasCredential,
            Boolean credentialActive,
            String username,
            String role
    ) {

        return (root, query, cb) -> {

            Predicate predicates =
                    baseFilters(
                            root,
                            cb,
                            organizationId,
                            name,
                            lastname
                    );

            Join<User, Credential> credentialJoin =
                    root.join(
                            "credential",
                            JoinType.LEFT
                    );

            // =========================
            // HAS CREDENTIAL
            // =========================

            if (hasCredential != null) {

                predicates = hasCredential
                        ? cb.and(
                        predicates,
                        cb.isNotNull(
                                credentialJoin.get("id")
                        )
                )
                        : cb.and(
                        predicates,
                        cb.isNull(
                                credentialJoin.get("id")
                        )
                );
            }

            // =========================
            // CREDENTIAL ACTIVE
            // =========================

            if (credentialActive != null) {

                predicates = cb.and(
                        predicates,
                        cb.equal(
                                credentialJoin.get("status"),
                                credentialActive
                                        ? StatusType.ACTIVE
                                        : StatusType.INACTIVE
                        )
                );
            }

            // =========================
            // USERNAME
            // =========================

            if (username != null
                    && !username.isBlank()) {

                predicates = cb.and(
                        predicates,
                        cb.like(
                                cb.lower(
                                        credentialJoin.get(
                                                "username"
                                        )
                                ),
                                "%" +
                                        username.toLowerCase()
                                        + "%"
                        )
                );
            }

            // =========================
            // ROLE
            // =========================

            if (role != null
                    && !role.isBlank()) {

                predicates = cb.and(
                        predicates,
                        cb.equal(
                                root.get("role")
                                        .get("value"),
                                role
                        )
                );
            }

            return predicates;
        };
    }
}