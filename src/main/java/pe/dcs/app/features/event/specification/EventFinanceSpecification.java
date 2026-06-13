package pe.dcs.app.features.event.specification;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.EventFinance;
import pe.dcs.app.util.enums.events.EventFinanceStatus;
import pe.dcs.app.util.enums.events.EventFinanceType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EventFinanceSpecification {

    private EventFinanceSpecification() {
    }

    public static Specification<EventFinance> filter(
            UUID eventId,
            EventFinanceType type,
            EventFinanceStatus status,
            LocalDate startDate,
            LocalDate endDate
    ) {

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (eventId == null) {
                return cb.disjunction();
            }

            predicates.add(
                    cb.equal(
                            root.get("event").get("id"),
                            eventId
                    )
            );

            if (type != null) {
                predicates.add(
                        cb.equal(root.get("type"), type)
                );
            }

            if (status != null) {
                predicates.add(
                        cb.equal(root.get("status"), status)
                );
            }

            if (startDate != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("transactionDate"),
                                startDate
                        )
                );
            }

            if (endDate != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("transactionDate"),
                                endDate
                        )
                );
            }

            return cb.and(
                    predicates.toArray(new Predicate[0])
            );
        };
    }
}