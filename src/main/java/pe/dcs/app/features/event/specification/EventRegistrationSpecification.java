package pe.dcs.app.features.event.specification;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import pe.dcs.app.entity.EventRegistration;
import pe.dcs.app.features.event.request.registration.EventRegistrationFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EventRegistrationSpecification {

    private EventRegistrationSpecification() {
    }

    public static Specification<EventRegistration> filter(
            EventRegistrationFilter filter,
            UUID organizationId
    ) {

        return (root, query, cb) -> {

            List<Predicate> predicates =
                    new ArrayList<>();

            if (filter != null) {

                if (filter.getEventId() == null) {
                    return cb.disjunction();
                }

                predicates.add(
                        cb.equal(
                                root.get("event").get("id"),
                                filter.getEventId()
                        )
                );
            }

            if (filter == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            if (filter.getEventId() != null) {

                predicates.add(
                        cb.equal(
                                root.get("event")
                                        .get("id"),
                                filter.getEventId()
                        )
                );
            }

            if (filter.getCategory() != null) {

                predicates.add(
                        cb.equal(
                                root.get("category"),
                                filter.getCategory()
                        )
                );
            }

            if (filter.getStatus() != null) {

                predicates.add(
                        cb.equal(
                                root.get("status"),
                                filter.getStatus()
                        )
                );
            }

            if (StringUtils.hasText(
                    filter.getName()
            )) {

                predicates.add(
                        cb.like(
                                cb.lower(
                                        root.get("name")
                                ),
                                "%" + filter.getName()
                                        .toLowerCase() + "%"
                        )
                );
            }

            if (StringUtils.hasText(
                    filter.getLastname()
            )) {

                predicates.add(
                        cb.like(
                                cb.lower(
                                        root.get("lastname")
                                ),
                                "%" + filter.getLastname()
                                        .toLowerCase() + "%"
                        )
                );
            }

            if (StringUtils.hasText(
                    filter.getPhone()
            )) {

                predicates.add(
                        cb.like(
                                root.get("phone"),
                                "%" + filter.getPhone() + "%"
                        )
                );
            }

            return cb.and(
                    predicates.toArray(
                            Predicate[]::new
                    )
            );
        };
    }
}