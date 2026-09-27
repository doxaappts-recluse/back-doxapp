package pe.dcs.app.features.plan.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID>, JpaSpecificationExecutor<Plan> {

    @Query("select count(p) > 0 from Plan p where upper(p.code) = upper(:code)")
    boolean codeTaken(String code);

    /** Planes (en cualquier estado) que incluyen el módulo. */
    @Query(value = "select count(*) from plan_module where module_code = :code", nativeQuery = true)
    long countByModule(String code);
}
