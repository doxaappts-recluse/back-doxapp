package pe.dcs.app.features.user.org_user;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.Organization;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.PersonBranch;
import pe.dcs.app.features.user.org_user.request.OrgUserFilter;
import pe.dcs.app.features.user.org_user.request.OrgUserSearchRequest;
import pe.dcs.app.security.service.AuthContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lista personas que tienen (o tuvieron alguna vez, sin importar
 * si ese PersonBranch sigue activo) al menos un PersonBranch
 * dentro de la organización Y sede del contexto de quien hace la
 * petición (ambas, siempre, tomadas de AuthContext). No depende
 * de si la persona tiene acceso al sistema (UserAccess/Credential).
 *
 * La sede ACTUAL (activa) de cada persona se informa aparte en
 * la respuesta (OrgUserMapper), ya que puede ser distinta de la
 * sede por la que fue encontrada acá.
 */
public class OrgUserSpecification {

    private OrgUserSpecification() {
    }

    public static Specification<Person> filter(
            OrgUserSearchRequest request,
            AuthContext authContext
    ) {

        return (root, query, cb) -> {

            query.distinct(true);

            List<Predicate> predicates = new ArrayList<>();

            OrgUserFilter filter =
                    request.getFilters() != null
                            ? request.getFilters()
                            : new OrgUserFilter();

            Join<Person, PersonBranch> personBranch =
                    root.join("branchHistory", JoinType.INNER);

            Join<PersonBranch, Branch> branch =
                    personBranch.join("branch", JoinType.INNER);

            Join<Branch, Organization> organization =
                    branch.join("organization", JoinType.INNER);

            UUID currentOrg = authContext.getCurrentOrganizationId();
            UUID currentBranch = authContext.getCurrentBranchId();

            if (currentOrg != null) {
                predicates.add(cb.equal(organization.get("id"), currentOrg));
            }

            if (currentBranch != null) {
                predicates.add(cb.equal(branch.get("id"), currentBranch));
            }

            if (hasText(filter.getName())) {
                predicates.add(
                        cb.like(cb.lower(root.get("name")), like(filter.getName()))
                );
            }

            if (hasText(filter.getLastname())) {
                predicates.add(
                        cb.like(cb.lower(root.get("lastname")), like(filter.getLastname()))
                );
            }

            if (hasText(filter.getSex())) {
                predicates.add(
                        cb.equal(cb.lower(root.get("sex")), filter.getSex().toLowerCase())
                );
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String like(String value) {
        return "%" + value.toLowerCase() + "%";
    }

}
