package pe.dcs.app.features.user.system_user;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.Credential;
import pe.dcs.app.entity.Role;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.UserAccess;
import pe.dcs.app.features.user.system_user.request.UserSystemFilter;
import pe.dcs.app.features.user.system_user.request.UserSystemListRequest;
import pe.dcs.app.util.enums.RoleType;

import java.util.ArrayList;
import java.util.List;

public class UserSystemSpecification {

    private UserSystemSpecification() {}

    public static Specification<Person> filter(UserSystemListRequest request) {

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            // =====================================================
            // SYSTEM USERS ONLY
            // =====================================================

            Join<Person, UserAccess> accessJoin =
                    root.join(
                            "accesses",
                            JoinType.INNER
                    );

            // Sin organización
            predicates.add(
                    cb.isNull(
                            accessJoin.get("organization")
                    )
            );

            // Sin sede
            predicates.add(
                    cb.isNull(
                            accessJoin.get("branch")
                    )
            );

            Join<UserAccess, Role> roleJoin =
                    accessJoin.join(
                            "role",
                            JoinType.INNER
                    );

            UserSystemFilter filters = request.getFilters();

            // =====================================================
            // ROLE FILTER
            // =====================================================

            if(filters != null && filters.getRoleId() != null){

                // Busca un rol específico
                predicates.add(
                        cb.equal(
                                roleJoin.get("id"),
                                filters.getRoleId()
                        )
                );

            }else{
                // Si no manda rol trae todos los roles del sistema
                predicates.add(
                        roleJoin.get("value")
                                .in(
                                        RoleType.SYSTEM_ADMIN,
                                        RoleType.SYSTEM_SUPPORT
                                )
                );
            }

            if(filters == null){
                return cb.and(
                        predicates.toArray(
                                new Predicate[0]
                        )
                );
            }

            // =====================================================
            // NAME
            // =====================================================

            if(filters.getName() != null && !filters.getName().isBlank()){

                predicates.add(
                        cb.like(
                                cb.lower(
                                        root.get("name")
                                ),
                                "%" +
                                        filters.getName()
                                                .toLowerCase()
                                        +
                                        "%"
                        )
                );
            }

            // =====================================================
            // LASTNAME
            // =====================================================

            if(filters.getLastname() != null && !filters.getLastname().isBlank()){

                predicates.add(
                        cb.like(
                                cb.lower(
                                        root.get("lastname")
                                ),
                                "%" +
                                        filters.getLastname()
                                                .toLowerCase()
                                        +
                                        "%"
                        )
                );
            }

            // =====================================================
            // USERNAME
            // =====================================================

            if(filters.getUsername() != null && !filters.getUsername().isBlank()){

                Join<Person, Credential> credentialJoin =
                        root.join(
                                "credential",
                                JoinType.INNER
                        );

                predicates.add(
                        cb.like(
                                cb.lower(
                                        credentialJoin.get("username")
                                ),
                                "%" +
                                        filters.getUsername()
                                                .toLowerCase()
                                        +
                                        "%"
                        )
                );
            }

            // =====================================================
            // STATUS
            // =====================================================

            if(filters.getStatus() != null){

                Join<Person, Credential> credentialJoin =
                        root.join(
                                "credential",
                                JoinType.INNER
                        );

                predicates.add(
                        cb.equal(
                                credentialJoin.get("status"),
                                filters.getStatus()
                        )
                );
            }

            return cb.and(
                    predicates.toArray(
                            new Predicate[0]
                    )
            );
        };
    }
}