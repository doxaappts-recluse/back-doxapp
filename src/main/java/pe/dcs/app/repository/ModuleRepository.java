package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.Module;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ModuleRepository extends JpaRepository<Module, UUID>,
        JpaSpecificationExecutor<Module> {

    // =========================================================
    // SIDEBAR
    // =========================================================

    List<Module> findByStatusOrderByOrderNumAsc(
            StatusType status
    );

    default List<Module> findAllActive() {
        return findByStatusOrderByOrderNumAsc(
                StatusType.ACTIVE
        );
    }

    // =========================================================
    // PADRES
    // =========================================================

    List<Module> findByParentIsNullAndStatusOrderByOrderNumAsc(
            StatusType status
    );

    List<Module> findByParentIsNullAndStatusAndIdNotOrderByOrderNumAsc(
            StatusType status,
            UUID id
    );

    // =========================================================
    // HIJOS
    // =========================================================

    List<Module> findByParentIsNotNullAndStatusOrderByOrderNumAsc(
            StatusType status
    );

    List<Module> findByParentIsNotNullAndStatusAndIdNotOrderByOrderNumAsc(
            StatusType status,
            UUID id
    );

    // =========================================================
    // VALIDACIONES
    // =========================================================

    Optional<Module> findByCodeAndStatus(
            String code,
            StatusType status
    );

    boolean existsByCodeIgnoreCase(
            String code
    );

    boolean existsByCodeIgnoreCaseAndIdNot(
            String code,
            UUID id
    );

    boolean existsByParent_Id(UUID parentId);

    boolean existsByParent_IdAndStatus(
            UUID parentId,
            StatusType status
    );

    boolean existsByParentIdAndStatus(UUID parentId, StatusType status);
}