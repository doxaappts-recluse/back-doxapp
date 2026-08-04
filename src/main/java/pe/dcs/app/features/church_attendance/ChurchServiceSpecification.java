package pe.dcs.app.features.church_attendance;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.ChurchService;
import pe.dcs.app.features.church_attendance.request.ChurchServiceFilterRequest;
import pe.dcs.app.security.service.AuthContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Asistencia a Cultos forma parte del paquete comercial "CRM Pastoral"
 * (junto a Seguimiento Pastoral/Visitantes) — mismo criterio de
 * scoping que {@link pe.dcs.app.features.bible_academy.BibleClassSpecification}:
 * ORG_ADMIN acotado a su organización (todas las sedes), cualquier
 * otro rol acotado a su sede actual. SYSTEM queda completamente
 * fuera — ver ChurchAttendanceAccessGuard (reutiliza
 * PastoralFollowUpAccessGuard).
 */
public class ChurchServiceSpecification {

    private ChurchServiceSpecification() {
    }

    public static Specification<ChurchService> filter(
            ChurchServiceFilterRequest filter,
            AuthContext authContext
    ) {

        ChurchServiceFilterRequest safeFilter =
                filter != null
                        ? filter
                        : new ChurchServiceFilterRequest();

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
