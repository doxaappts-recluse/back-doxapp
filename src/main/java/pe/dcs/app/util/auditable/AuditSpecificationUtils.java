package pe.dcs.app.util.auditable;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.util.StringUtils;

import java.util.List;

public final class AuditSpecificationUtils {

    private AuditSpecificationUtils() {
    }

    public static void applyAuditFilters(
            Root<?> root,
            CriteriaBuilder cb,
            List<Predicate> predicates,
            AuditableFilter filter
    ) {

        if (filter == null) {
            return;
        }

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

        if (StringUtils.hasText(filter.getCreatedBy())) {

            String search =
                    "%" + filter.getCreatedBy().toLowerCase() + "%";

            predicates.add(
                    cb.or(
                            cb.like(
                                    cb.lower(
                                            root.get("createdBy")
                                                    .get("name")
                                    ),
                                    search
                            ),
                            cb.like(
                                    cb.lower(
                                            root.get("createdBy")
                                                    .get("lastname")
                                    ),
                                    search
                            )
                    )
            );
        }

        if (StringUtils.hasText(filter.getUpdatedBy())) {

            String search =
                    "%" + filter.getUpdatedBy().toLowerCase() + "%";

            predicates.add(
                    cb.or(
                            cb.like(
                                    cb.lower(
                                            root.get("updatedBy")
                                                    .get("name")
                                    ),
                                    search
                            ),
                            cb.like(
                                    cb.lower(
                                            root.get("updatedBy")
                                                    .get("lastname")
                                    ),
                                    search
                            )
                    )
            );
        }
    }
}