package pe.dcs.app.features.branch;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.features.branch.request.BranchListRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BranchSpecification {

    private BranchSpecification() { }

    public static Specification<Branch> filter(
            UUID organizationId,
            BranchListRequest request
    ) {

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            predicates.add(
                    cb.equal(
                            root.get("organization").get("id"),
                            organizationId
                    )
            );

            /*if(StringUtils.hasText(request.getSearch())){

                String search =
                        "%" + request.getSearch().toLowerCase() + "%";

                predicates.add(
                        cb.or(
                                cb.like(
                                        cb.lower(root.get("name")),
                                        search
                                ),
                                cb.like(
                                        cb.lower(root.get("code")),
                                        search
                                )
                        )
                );
            }*/

            return cb.and(
                    predicates.toArray(new Predicate[0])
            );
        };
    }
}