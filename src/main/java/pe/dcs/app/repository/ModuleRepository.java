package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.Module;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

@Repository
public interface ModuleRepository extends JpaRepository<Module, UUID>, JpaSpecificationExecutor<Module> {

    @Query("""
        SELECT m
        FROM Module m
        WHERE m.status = 'ACTIVE'
    """)
    List<Module> findAllActive();

    // =========================================
    // PARENT MODULES
    // =========================================

    List<Module> findByParentIsNullAndStatus(StatusType status);

    List<Module> findByParentIsNullAndStatusAndIdNot(
            StatusType status,
            UUID id
    );

    // =========================================
    // CHILD MODULES
    // =========================================

    List<Module> findByParentIsNotNullAndStatus(StatusType status);

    List<Module> findByParentIsNotNullAndStatusAndIdNot(
            StatusType status,
            UUID id
    );
}
