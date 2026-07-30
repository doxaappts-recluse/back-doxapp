package pe.dcs.app.features.user.access_user;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.*;
import pe.dcs.app.features.user.access_user.request.AccessUserFilter;
import pe.dcs.app.features.user.access_user.request.AccessUserListRequest;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.enums.StatusType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lista accesos (UserAccess) con rol ORG_USER: usuarios de
 * operación a los que se les asignan módulos/permisos por
 * contrato, uno por acceso (una persona puede tener varios
 * accesos ORG_USER, uno por sede).
 *
 * A diferencia de la versión anterior (rooteada en Person), acá
 * se roota directamente en UserAccess para que el filtro de
 * sede/organización quede correlacionado con el MISMO acceso que
 * se está listando, en vez de con el historial (PersonBranch) de
 * la persona.
 *
 * Solo lo usan ORG_ADMIN / ORG_BRANCH_ADMIN (SYSTEM no
 * entra acá, se valida antes en el service).
 */
public class AccessUserSpecification {

    private AccessUserSpecification() {
    }

    public static Specification<UserAccess> filter(
            AccessUserListRequest request,
            AuthContext authContext
    ) {

        return (root, query, cb) -> {

            query.distinct(true);

            List<Predicate> predicates = new ArrayList<>();

            AccessUserFilter filter =
                    request.getFilters() != null
                            ? request.getFilters()
                            : new AccessUserFilter();

            /*
             * =====================================
             * ROL FIJO: ORG_USER + ACCESO ACTIVO
             * =====================================
             */

            Join<UserAccess, Role> role =
                    root.join("role", JoinType.INNER);

            predicates.add(
                    cb.equal(
                            role.get("value"),
                            RoleType.ORG_USER
                    )
            );

            predicates.add(
                    cb.equal(
                            root.get("active"),
                            StatusType.ACTIVE
                    )
            );

            /*
             * =====================================
             * ORGANIZACION: siempre la del contexto
             * (SYSTEM no llega a este specification)
             * =====================================
             */

            Join<UserAccess, Organization> organization =
                    root.join("organization", JoinType.INNER);

            UUID currentOrg =
                    authContext.getCurrentOrganizationId();

            if (currentOrg != null) {

                predicates.add(
                        cb.equal(
                                organization.get("id"),
                                currentOrg
                        )
                );

            }

            /*
             * =====================================
             * SEDE (la del ACCESO, no la del historial
             * de la persona)
             *
             * ORG_ADMIN: si manda sede filtra, sino todas.
             * ORG_BRANCH_ADMIN: si el frontend no manda
             * sede, se acota por defecto a la suya.
             * =====================================
             */

            Join<UserAccess, Branch> branch =
                    root.join("branch", JoinType.LEFT);

            UUID effectiveBranchId = filter.getBranchId();

            if (effectiveBranchId == null
                    && !authContext.isCurrentOrganizationAdmin()
                    && authContext.isCurrentBranchAdmin()) {

                effectiveBranchId =
                        authContext.getCurrentBranchId();

            }

            if (effectiveBranchId != null) {

                predicates.add(
                        cb.equal(
                                branch.get("id"),
                                effectiveBranchId
                        )
                );

            }

            /*
             * =====================================
             * DATOS PERSONA
             * =====================================
             */

            Join<UserAccess, Person> person =
                    root.join("person", JoinType.INNER);

            if (hasText(filter.getName())) {

                predicates.add(
                        cb.like(
                                cb.lower(person.get("name")),
                                like(filter.getName())
                        )
                );

            }

            if (hasText(filter.getLastname())) {

                predicates.add(
                        cb.like(
                                cb.lower(person.get("lastname")),
                                like(filter.getLastname())
                        )
                );

            }

            if (hasText(filter.getDni())) {

                predicates.add(
                        cb.like(
                                person.get("dni"),
                                "%" + filter.getDni() + "%"
                        )
                );

            }

            /*
             * =====================================
             * CREDENTIAL
             * =====================================
             */

            Join<Person, Credential> credential =
                    person.join("credential", JoinType.LEFT);

            if (hasText(filter.getUsername())) {

                predicates.add(
                        cb.like(
                                cb.lower(
                                        credential.get("username")
                                ),
                                like(filter.getUsername())
                        )
                );

            }

            if (filter.getHasCredential() != null) {

                if (filter.getHasCredential()) {

                    predicates.add(
                            cb.isNotNull(
                                    credential.get("id")
                            )
                    );

                } else {

                    predicates.add(
                            cb.isNull(
                                    credential.get("id")
                            )
                    );

                }

            }

            if (filter.getCredentialActive() != null) {

                if (filter.getCredentialActive()) {

                    predicates.add(
                            cb.equal(
                                    credential.get("status"),
                                    StatusType.ACTIVE
                            )
                    );

                } else {

                    predicates.add(
                            cb.notEqual(
                                    credential.get("status"),
                                    StatusType.ACTIVE
                            )
                    );

                }

            }

            return cb.and(
                    predicates.toArray(new Predicate[0])
            );

        };

    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String like(String value) {
        return "%" + value.toLowerCase() + "%";
    }

}
