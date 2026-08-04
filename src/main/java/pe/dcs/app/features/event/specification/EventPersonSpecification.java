package pe.dcs.app.features.event.specification;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.Membership;
import pe.dcs.app.entity.Organization;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.PersonBranch;
import pe.dcs.app.features.event.request.registration.EventPersonSearchRequest;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.events.RegistrationCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Busca personas ya registradas en la organización para inscribir
 * como Miembro o Staff en un evento.
 *
 * - organizationId: siempre se aplica.
 * - branchId: si viene null significa búsqueda org-wide (solo
 *   org admin, y solo cuando el evento es scope=ORGANIZATION — ver
 *   EventRegistrationServiceImpl.searchPersons); si viene con
 *   valor, se restringe a esa sede puntual.
 * - category=MEMBER: exige una membresía vigente (current=true) Y
 *   activa (status=ACTIVE). category=STAFF: no filtra por
 *   membresía, cualquier persona registrada en el alcance califica.
 */
public class EventPersonSpecification {

    private EventPersonSpecification() {
    }

    public static Specification<Person> filter(
            EventPersonSearchRequest request,
            UUID organizationId,
            UUID branchId
    ) {

        return (root, query, cb) -> {

            query.distinct(true);

            List<Predicate> predicates = new ArrayList<>();

            Join<Person, PersonBranch> personBranch =
                    root.join("branchHistory", JoinType.INNER);

            Join<PersonBranch, Branch> branch =
                    personBranch.join("branch", JoinType.INNER);

            Join<Branch, Organization> organization =
                    branch.join("organization", JoinType.INNER);

            if (organizationId != null) {
                predicates.add(cb.equal(organization.get("id"), organizationId));
            }

            if (branchId != null) {
                predicates.add(cb.equal(branch.get("id"), branchId));
            }

            if (request.getCategory() == RegistrationCategory.MEMBER) {

                Join<Person, Membership> membership =
                        root.join("memberships", JoinType.INNER);

                predicates.add(cb.isTrue(membership.get("current")));
                predicates.add(cb.equal(membership.get("status"), StatusType.ACTIVE));
            }

            if (StringUtils.hasText(request.getName())) {

                String like =
                        "%" + request.getName().toLowerCase() + "%";

                predicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("name")), like),
                                cb.like(cb.lower(root.get("lastname")), like)
                        )
                );
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
