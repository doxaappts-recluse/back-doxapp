package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.UserAccessModule;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAccessModuleRepository extends JpaRepository<UserAccessModule, UUID> {

    @Query("""
        SELECT uam.module.id
        FROM UserAccessModule uam
        WHERE uam.userAccess.person.id = :personId
          AND uam.userAccess.active = :status
          AND uam.status = 'ACTIVE'
          AND uam.enabled = true
    """)
    List<UUID> findActiveModuleIdsByPersonId(
            @Param("personId") UUID personId,
            @Param("status") StatusType status
    );

}