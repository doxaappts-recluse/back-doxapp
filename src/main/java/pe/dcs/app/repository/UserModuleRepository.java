package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.UserModule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserModuleRepository extends JpaRepository<UserModule, UUID> {

    @Query("""
        SELECT um.module.id
        FROM UserModule um
        WHERE um.user.id = :userId
          AND um.status = 'ACTIVE'
          AND um.enabled = true
    """)
    List<UUID> findByUserId(UUID userId);

    @Query("""
        SELECT um.module.id
        FROM UserModule um
        WHERE um.user.id = :userId
          AND um.enabled = true
    """)
    List<UUID> findModuleIdsByUserId(UUID userId);

    Optional<UserModule> findByUserIdAndModuleId(UUID userId, UUID moduleId);

    void deleteByUserId(UUID userId);

    @Query("""
        SELECT um
        FROM UserModule um
        WHERE um.user.id = :userId
          AND um.status = 'ACTIVE'
          AND um.enabled = true
    """)
    List<UserModule> findAllByUserId(UUID userId);
}