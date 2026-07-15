package pe.dcs.app.features.module;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.Module;
import pe.dcs.app.util.enums.StatusType;

public class ModuleSpecification {

    public static Specification<Module> filter(String name, String code, StatusType status) {

        return (root, query, cb) -> {

            Predicate p = cb.conjunction();

            if (name != null && !name.isBlank()) {
                p = cb.and(
                        p,
                        cb.like(
                                cb.lower(root.get("name")),
                                "%" + name.toLowerCase() + "%"
                        )
                );
            }

            if (code != null && !code.isBlank()) {
                p = cb.and(
                        p,
                        cb.like(
                                cb.lower(root.get("code")),
                                "%" + code.toLowerCase() + "%"
                        )
                );
            }

            if (status != null) {
                p = cb.and(
                        p,
                        cb.equal(
                                root.get("status"),
                                status
                        )
                );
            }

            return p;
        };
    }
}