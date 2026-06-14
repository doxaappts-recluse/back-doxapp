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

            if (filter == null) {
                return cb.conjunction();
            }

            if (filter.getEventId() == null) {
                return cb.disjunction();
            }

            predicates.add(
                    cb.equal(
                            root.get("event").get("id"),
                            filter.getEventId()
                    )
            );

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

            if (StringUtils.hasText(filter.getName())) {

                predicates.add(
                        cb.like(
                                cb.lower(root.get("name")),
                                "%" + filter.getName().toLowerCase() + "%"
                        )
                );
            }

            if (StringUtils.hasText(filter.getLastname())) {

                predicates.add(
                        cb.like(
                                cb.lower(root.get("lastname")),
                                "%" + filter.getLastname().toLowerCase() + "%"
                        )
                );
            }

            if (StringUtils.hasText(filter.getPhone())) {

                predicates.add(
                        cb.like(
                                root.get("phone"),
                                "%" + filter.getPhone() + "%"
                        )
                );
            }

            // =========================
            // CREATED AT
            // =========================

            if (filter.getCreatedAtFrom() != null) {

                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("createdAt"),
                                filter.getCreatedAtFrom()
                        )
                );
            }

            if (filter.getCreatedAtTo() != null) {

                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("createdAt"),
                                filter.getCreatedAtTo()
                        )
                );
            }

            // =========================
            // UPDATED AT
            // =========================

            if (filter.getUpdatedAtFrom() != null) {

                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("updatedAt"),
                                filter.getUpdatedAtFrom()
                        )
                );
            }

            if (filter.getUpdatedAtTo() != null) {

                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("updatedAt"),
                                filter.getUpdatedAtTo()
                        )
                );
            }

            // =========================
            // CREATED BY
            // =========================

            if (StringUtils.hasText(filter.getCreatedBy())) {

                String search =
                        "%" + filter.getCreatedBy().toLowerCase() + "%";

                predicates.add(
                        cb.like(
                                cb.lower(
                                        cb.concat(
                                                cb.concat(
                                                        root.get("createdBy").get("name"),
                                                        " "
                                                ),
                                                root.get("createdBy").get("lastname")
                                        )
                                ),
                                search
                        )
                );
            }

            // =========================
            // UPDATED BY
            // =========================

            if (StringUtils.hasText(filter.getUpdatedBy())) {

                String search =
                        "%" + filter.getUpdatedBy().toLowerCase() + "%";

                predicates.add(
                        cb.like(
                                cb.lower(
                                        cb.concat(
                                                cb.concat(
                                                        root.get("updatedBy").get("name"),
                                                        " "
                                                ),
                                                root.get("updatedBy").get("lastname")
                                        )
                                ),
                                search
                        )
                );
            }

            return cb.and(
                    predicates.toArray(new Predicate[0])
            );
        };
    }
}