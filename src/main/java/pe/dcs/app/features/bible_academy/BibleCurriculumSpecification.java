package pe.dcs.app.features.bible_academy;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.BibleCurriculum;
import pe.dcs.app.features.bible_academy.request.BibleCurriculumFilterRequest;
import pe.dcs.app.security.service.AuthContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Scope: siempre por organización actual (la malla es compartida por
 * todas las sedes) — cualquier rol con acceso al módulo puede
 * listarla/verla (para elegir cursos al abrir un dictado), aunque
 * solo el org admin puede gestionarla (ver
 * BibleAcademyAccessGuard.assertCanManageCurriculum). SYSTEM queda
 * completamente fuera.
 */
public class BibleCurriculumSpecification {

    private BibleCurriculumSpecification() {
    }

    public static Specification<BibleCurriculum> filter(
            BibleCurriculumFilterRequest filter,
            AuthContext authContext
    ) {

        BibleCurriculumFilterRequest safeFilter =
                filter != null
                        ? filter
                        : new BibleCurriculumFilterRequest();

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            predicates.add(
                    cb.equal(
                            root.get("organization").get("id"),
                            authContext.getCurrentOrganizationId()
                    )
            );

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
