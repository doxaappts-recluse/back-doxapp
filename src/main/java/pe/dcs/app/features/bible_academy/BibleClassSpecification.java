package pe.dcs.app.features.bible_academy;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.BibleClass;
import pe.dcs.app.features.bible_academy.request.BibleClassFilterRequest;
import pe.dcs.app.security.service.AuthContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Scope: org admin ve todos los dictados de su organización, branch
 * admin/org user delegado solo los de su sede actual. SYSTEM queda
 * completamente fuera — ver BibleAcademyAccessGuard.
 */
public class BibleClassSpecification {

    private BibleClassSpecification() {
    }

    public static Specification<BibleClass> filter(
            BibleClassFilterRequest filter,
            AuthContext authContext
    ) {

        BibleClassFilterRequest safeFilter =
                filter != null
                        ? filter
                        : new BibleClassFilterRequest();

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (authContext.isCurrentOrganizationAdmin()) {

                predicates.add(
                        cb.equal(
                                root.get("branch").get("organization").get("id"),
                                authContext.getCurrentOrganizationId()
                        )
                );

            } else {

                predicates.add(
                        cb.equal(
                                root.get("branch").get("id"),
                                authContext.getCurrentBranchId()
                        )
                );
            }

            if (safeFilter.getBranchId() != null) {
                predicates.add(
                        cb.equal(root.get("branch").get("id"), safeFilter.getBranchId())
                );
            }

            if (safeFilter.getCourseId() != null) {
                predicates.add(
                        cb.equal(root.get("course").get("id"), safeFilter.getCourseId())
                );
            }

            if (hasText(safeFilter.getCourseName())) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("course").get("name")),
                                like(safeFilter.getCourseName())
                        )
                );
            }

            if (safeFilter.getStatus() != null) {
                predicates.add(
                        cb.equal(root.get("status"), safeFilter.getStatus())
                );
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
