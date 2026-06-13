package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.dcs.app.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.dcs.app.features.rol.response.RoleResponse;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID>, JpaSpecificationExecutor<Role> {
    List<Role> findByStatus(StatusType status);

    @Query("""
        SELECT r
        FROM Role r
        WHERE r.value LIKE :prefix% AND r.status = :status
    """)
    List<RoleResponse> findByPrefixAndStatus(
            @Param("prefix") String prefix,
            @Param("status") StatusType status
    );

    Optional<Role> findByValue(String value);

}
