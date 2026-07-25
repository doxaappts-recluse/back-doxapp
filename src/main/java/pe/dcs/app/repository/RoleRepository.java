package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.dcs.app.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.dcs.app.features.rol.response.RoleResponse;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.enums.StatusType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID>, JpaSpecificationExecutor<Role> {
    List<Role> findByStatus(StatusType status);

    List<Role> findByValueInAndStatus(
            List<RoleType> values,
            StatusType status
    );

    Optional<Role> findByValue(RoleType value);

    List<Role> findByStatusAndValueNotIn(
            StatusType status,
            Collection<RoleType> values
    );

}
