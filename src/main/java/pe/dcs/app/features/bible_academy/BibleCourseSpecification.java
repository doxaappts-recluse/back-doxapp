package pe.dcs.app.features.bible_academy;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.BibleCourse;
import pe.dcs.app.features.bible_academy.request.BibleCourseFilterRequest;
import pe.dcs.app.security.service.AuthContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Búsqueda paginada de cursos — en la práctica, solo se usa para
 * "Cursos Extra" (curriculum == null), scopeado por sede: org admin
 * ve toda su organización, cualquier otro rol solo su sede actual.
 * Los niveles de una malla (curriculum != null) NO se listan acá —
 * se ven embebidos en BibleCurriculumDetailResponse.courses (ver
 * BibleAcademyServiceImpl.getCurriculumById) porque son una
 * jerarquía fija y ordenada, no un listado a filtrar/paginar. Si se
 * informa filters.curriculumId, se busca dentro de esa malla puntual
 * en cambio (uso interno, no expuesto por defecto en la pantalla de
 * Cursos Extra). SYSTEM queda completamente fuera — ver
 * BibleAcademyAccessGuard.
 */
public class BibleCourseSpecification {

    private BibleCourseSpecification() {
    }

    public static Specification<BibleCourse> filter(
            BibleCourseFilterRequest filter,
            AuthContext authContext
    ) {

        BibleCourseFilterRequest safeFilter =
                filter != null
                        ? filter
                        : new BibleCourseFilterRequest();

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (safeFilter.getCurriculumId() != null) {

                predicates.add(
                        cb.equal(root.get("curriculum").get("id"), safeFilter.getCurriculumId())
                );

                predicates.add(
                        cb.equal(
                                root.get("curriculum").get("organization").get("id"),
                                authContext.getCurrentOrganizationId()
                        )
                );

            } else {

                predicates.add(cb.isNull(root.get("curriculum")));

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
            }

            if (hasText(safeFilter.getName())) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("name")),
                                like(safeFilter.getName())
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
