package pe.dcs.app.features.ministry;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.dcs.app.entity.Ministry;
import pe.dcs.app.util.enums.StatusType;

import java.util.ArrayList;
import java.util.List;

public class MinistrySpecification {

    public static Specification<Ministry> filter(
            String name,
            StatusType status
    ){

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            if(name != null && !name.isBlank()){

                predicates.add(
                        cb.like(
                                cb.lower(root.get("name")),
                                "%" + name.toLowerCase() + "%"
                        )
                );

            }

            if(status != null){

                predicates.add(
                        cb.equal(
                                root.get("status"),
                                status
                        )
                );

            }

            return cb.and(predicates.toArray(new Predicate[0]));

        };

    }
}