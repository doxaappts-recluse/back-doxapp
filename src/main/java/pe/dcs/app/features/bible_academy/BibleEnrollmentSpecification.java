package pe.dcs.app.features.bible_academy;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.BibleEnrollment;
import pe.dcs.app.features.bible_academy.request.BibleEnrollmentFilterRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Siempre scopeada a un dictado puntual (classId viene del path, ver
 * BibleAcademyController) — el acceso a ese dictado ya se valida
 * antes vía BibleAcademyAccessGuard.assertCanManageClass, así que
 * acá no se repite el scope org/sede.
 */
public class BibleEnrollmentSpecification {

    private BibleEnrollmentSpecification() {
    }

    public static Specification<BibleEnrollment> filter(
            UUID bibleClassId,
            BibleEnrollmentFilterRequest filter
    ) {

        BibleEnrollmentFilterRequest safeFilter =
                filter != null
                        ? filter
                        : new BibleEnrollmentFilterRequest();

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            predicates.add(
                    cb.equal(root.get("bibleClass").get("id"), bibleClassId)
            );

            if (hasText(safeFilter.getPersonName())) {

                String pattern = like(safeFilter.getPersonName());

                predicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("person").get("name")), pattern),
                                cb.like(cb.lower(root.get("person").get("lastname")), pattern)
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
