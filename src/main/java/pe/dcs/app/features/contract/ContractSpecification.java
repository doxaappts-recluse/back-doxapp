package pe.dcs.app.features.contract;

import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.Contract;
import pe.dcs.app.features.contract.request.ContractFilterRequest;

import java.util.ArrayList;
import java.util.List;

public class ContractSpecification {

    private ContractSpecification(){}

    public static Specification<Contract> filter(
            ContractFilterRequest filters
    ) {

        return (root, query, cb) -> {

            List<Predicate> predicates =
                    new ArrayList<>();

            if (filters == null) {

                return cb.and(
                        predicates.toArray(new Predicate[0])
                );
            }

            // =========================================
            // ORGANIZATION
            // =========================================

            if (filters.getOrganizationId() != null) {

                predicates.add(
                        cb.equal(
                                root.get("organization")
                                        .get("id"),
                                filters.getOrganizationId()
                        )
                );
            }

            // =========================================
            // PLAN
            // =========================================

            if (filters.getPlanName() != null &&
                    !filters.getPlanName().isBlank()) {

                predicates.add(
                        cb.like(
                                cb.lower(root.get("planName")),
                                "%" +
                                        filters.getPlanName()
                                                .toLowerCase()
                                        + "%"
                        )
                );
            }

            // =========================================
            // STATUS
            // =========================================

            if (filters.getStatus() != null) {

                predicates.add(
                        cb.equal(
                                root.get("status"),
                                filters.getStatus()
                        )
                );
            }

            // =========================================
            // RENEWAL TYPE
            // =========================================

            if (filters.getRenewalType() != null) {

                predicates.add(
                        cb.equal(
                                root.get("renewalType"),
                                filters.getRenewalType()
                        )
                );
            }

            // =========================================
            // START DATE
            // =========================================

            if (filters.getStartDate() != null) {

                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("startDate"),
                                filters.getStartDate()
                        )
                );
            }

            // =========================================
            // END DATE
            // =========================================

            if (filters.getEndDate() != null) {

                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("endDate"),
                                filters.getEndDate()
                        )
                );
            }

            return cb.and(
                    predicates.toArray(new Predicate[0])
            );
        };
    }
}