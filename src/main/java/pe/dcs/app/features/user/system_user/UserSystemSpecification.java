package pe.dcs.app.features.user.system_user;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.Credential;
import pe.dcs.app.entity.Role;
import pe.dcs.app.entity.User;
import pe.dcs.app.features.user.system_user.request.UserSystemFilter;
import pe.dcs.app.features.user.system_user.request.UserSystemListRequest;

import java.util.ArrayList;
import java.util.List;

public class UserSystemSpecification {

    private UserSystemSpecification() {}

    public static Specification<User> filter(
            UserSystemListRequest request, String typeOrg
    ) {

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            // =====================================================
            // SCOPE SYSTEM USERS
            // =====================================================
            predicates.add(
                    cb.like(
                            root.get("role").get("value"), typeOrg

                    )
            );

            UserSystemFilter filters = request.getFilters();

            if (filters == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            Join<User, Credential> credentialJoin = null;
            Join<User, Role> roleJoin = null;

            // NAME
            if (filters.getName() != null && !filters.getName().isBlank()) {
                predicates.add(
                        cb.like(cb.lower(root.get("name")),
                                "%" + filters.getName().toLowerCase() + "%")
                );
            }

            // LASTNAME
            if (filters.getLastname() != null && !filters.getLastname().isBlank()) {
                predicates.add(
                        cb.like(cb.lower(root.get("lastname")),
                                "%" + filters.getLastname().toLowerCase() + "%")
                );
            }

            // USERNAME
            if (filters.getUsername() != null && !filters.getUsername().isBlank()) {

                credentialJoin = root.join("credential");

                predicates.add(
                        cb.like(
                                cb.lower(credentialJoin.get("username")),
                                "%" + filters.getUsername().toLowerCase() + "%"
                        )
                );
            }

            // ROLE
            if (filters.getRoleId() != null) {

                roleJoin = root.join("role");

                predicates.add(
                        cb.equal(roleJoin.get("id"), filters.getRoleId())
                );
            }

            // STATUS
            if (filters.getStatus() != null) {

                if (credentialJoin == null) {
                    credentialJoin = root.join("credential");
                }

                predicates.add(
                        cb.equal(
                                credentialJoin.get("status"),
                                filters.getStatus()
                        )
                );
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}