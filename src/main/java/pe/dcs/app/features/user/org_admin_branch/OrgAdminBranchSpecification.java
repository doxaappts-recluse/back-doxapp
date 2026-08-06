package pe.dcs.app.features.user.org_admin_branch;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.*;
import pe.dcs.app.features.user.org_admin_branch.request.OrgAdminBranchFilter;
import pe.dcs.app.features.user.org_admin_branch.request.OrgAdminBranchListRequest;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.enums.StatusType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class OrgAdminBranchSpecification {

    private OrgAdminBranchSpecification() {
    }

    public static Specification<Person> filter(
            OrgAdminBranchListRequest request,
            AuthContext authContext
    ) {

        return (root, query, cb) -> {

            query.distinct(true);

            List<Predicate> predicates = new ArrayList<>();

            OrgAdminBranchFilter filter =
                    request.getFilters() != null
                            ? request.getFilters()
                            : new OrgAdminBranchFilter();

            /*
             * =====================================
             * ACCESO ACTIVO (organización / sede)
             * =====================================
             *
             * Person
             *    |
             * UserAccess(active)
             *    |___ Organization
             *    |___ Branch (NULL para ORG_ADMIN: acceso global)
             *
             * OJO: antes esto se resolvía vía branchHistory
             * (Person -> PersonBranch(active) -> Branch -> Organization)
             * con INNER JOIN. Un ORG_ADMIN no tiene sede (es acceso
             * global) y por lo tanto nunca tiene un PersonBranch, así
             * que ese INNER JOIN lo excluía SIEMPRE de este listado
             * (bug: un ORG_ADMIN recién creado no aparecía). Se
             * resuelve organización/sede a través del propio
             * UserAccess (ya usado también por el mapper), que sí
             * existe para todos los roles de este módulo.
             */

            Join<Person, UserAccess> orgAccess =
                    root.join(
                            "accesses",
                            JoinType.INNER
                    );

            predicates.add(
                    cb.equal(
                            orgAccess.get("active"),
                            StatusType.ACTIVE
                    )
            );

            Join<UserAccess, Organization> organization =
                    orgAccess.join(
                            "organization",
                            JoinType.LEFT
                    );

            Join<UserAccess, Branch> branch =
                    orgAccess.join(
                            "branch",
                            JoinType.LEFT
                    );

            /*
             * =====================================
             * ORGANIZACION
             * =====================================
             *
             * SYSTEM:
             * - si manda org filtra
             * - sino todas
             *
             * ORG_ADMIN:
             * - usa contexto
             *
             */

            if(authContext.isSystem()) {

                if(filter.getOrganizationId() != null) {

                    predicates.add(
                            cb.equal(
                                    organization.get("id"),
                                    filter.getOrganizationId()
                            )
                    );

                }

            } else {

                UUID currentOrg =
                        authContext.getCurrentOrganizationId();

                if(currentOrg != null) {

                    predicates.add(
                            cb.equal(
                                    organization.get("id"),
                                    currentOrg
                            )
                    );

                }

            }

            /*
             * =====================================
             * SEDE
             * =====================================
             *
             * SYSTEM / ORG_ADMIN:
             * - si mandan sede filtra, sino todas (las de la org)
             *
             * ORG_BRANCH_ADMIN (admin de sede, no de organización):
             * - si el frontend no manda sede, se acota por
             *   defecto a su propia sede (no ve otras sedes).
             *
             */

            UUID effectiveBranchId = filter.getBranchId();

            if(effectiveBranchId == null
                    && !authContext.isSystem()
                    && !authContext.isCurrentOrganizationAdmin()
                    && authContext.isCurrentBranchAdmin()) {

                effectiveBranchId =
                        authContext.getCurrentBranchId();

            }

            if(effectiveBranchId != null) {

                predicates.add(
                        cb.equal(
                                branch.get("id"),
                                effectiveBranchId
                        )
                );

            }

            /*
             * =====================================
             * ROLES
             * =====================================
             */

            if(filter.getRole() != null) {

                Join<Person, UserAccess> access =
                        root.join(
                                "accesses",
                                JoinType.INNER
                        );

                Join<UserAccess, Role> role =
                        access.join(
                                "role",
                                JoinType.INNER
                        );

                predicates.add(
                        cb.equal(
                                role.get("value"),
                                filter.getRole()
                        )
                );

            }

            /*
             * =====================================
             * DATOS PERSONA
             * =====================================
             */

            if(hasText(filter.getName())) {

                predicates.add(
                        cb.like(
                                cb.lower(root.get("name")),
                                like(filter.getName())
                        )
                );

            }

            if(hasText(filter.getLastname())) {

                predicates.add(
                        cb.like(
                                cb.lower(root.get("lastname")),
                                like(filter.getLastname())
                        )
                );

            }

            if(hasText(filter.getDni())) {

                predicates.add(
                        cb.like(
                                root.get("dni"),
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
                    root.join(
                            "credential",
                            JoinType.LEFT
                    );

            if(hasText(filter.getUsername())) {

                predicates.add(
                        cb.like(
                                cb.lower(
                                        credential.get("username")
                                ),
                                like(filter.getUsername())
                        )
                );

            }

            if(filter.getHasCredential() != null) {

                if(filter.getHasCredential()) {

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

            if(filter.getCredentialActive() != null) {

                if(filter.getCredentialActive()) {

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

    private static boolean hasText(String value){
        return value != null && !value.isBlank();
    }

    private static String like(String value){
        return "%" + value.toLowerCase() + "%";
    }

}