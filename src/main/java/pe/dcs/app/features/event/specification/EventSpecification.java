package pe.dcs.app.features.event.specification;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.Event;
import pe.dcs.app.util.enums.events.EventStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EventSpecification {

    private EventSpecification() {
    }

    public static Specification<Event> filter(
            UUID organizationId,
            String name,
            EventStatus status,
            LocalDateTime startDateFrom,
            LocalDateTime startDateTo
    ) {

        return (root, query, cb) -> {

            List<Predicate> predicates =
                    new ArrayList<>();

            predicates.add(
                    cb.equal(
                            root.get("organization")
                                    .get("id"),
                            organizationId
                    )
            );

            if (name != null && !name.isBlank()) {

                predicates.add(
                        cb.like(
                                cb.lower(root.get("name")),
                                "%" + name.toLowerCase() + "%"
                        )
                );
            }

            if (status != null) {

                predicates.add(
                        cb.equal(
                                root.get("status"),
                                status
                        )
                );
            }

            if (startDateFrom != null) {

                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("startDateTime"),
                                startDateFrom
                        )
                );
            }

            if (startDateTo != null) {

                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("startDateTime"),
                                startDateTo
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