package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.UserModule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserModuleRepository extends JpaRepository<UserModule, UUID> {

    // =========================================================
    // MODULOS ACTIVOS ASIGNADOS AL USUARIO
    // =========================================================

    @Query("""
        SELECT um.module.id
        FROM UserModule um
        WHERE um.user.id = :userId
          AND um.status = 'ACTIVE'
          AND um.enabled = true
    """)
    List<UUID> findModuleIdsByUserId(
            @Param("userId") UUID userId
    );

    // =========================================================
    // DETALLE DE MODULOS DEL USUARIO
    // =========================================================

    @Query("""
        SELECT um
        FROM UserModule um
        JOIN FETCH um.module m
        WHERE um.user.id = :userId
          AND um.status = 'ACTIVE'
          AND um.enabled = true
    """)
    List<UserModule> findActiveByUserId(
            @Param("userId") UUID userId
    );

    // =========================================================
    // VALIDACION
    // =========================================================

    @Query("""
        SELECT COUNT(um) > 0
        FROM UserModule um
        WHERE um.user.id = :userId
          AND um.module.id = :moduleId
          AND um.status = 'ACTIVE'
          AND um.enabled = true
    """)
    boolean existsActiveModule(
            @Param("userId") UUID userId,
            @Param("moduleId") UUID moduleId
    );

    // =========================================================
    // ADMINISTRACION
    // =========================================================

    Optional<UserModule>
    findByUserIdAndModuleId(
            UUID userId,
            UUID moduleId
    );

    void deleteByUserId(
            UUID userId
    );

    @Query("""
        SELECT um.module.id
        FROM UserModule um
        WHERE um.user.id = :userId
        AND um.enabled = true
        AND um.status = 'ACTIVE'
    """)
    List<UUID> findEnabledModuleIdsByUserId(
            @Param("userId") UUID userId
    );

    @Query("""
        SELECT p.code
        FROM UserModulePermission ump
        JOIN ump.permission p
        JOIN ump.userModule um
        WHERE um.user.id = :userId
          AND um.module.id = :moduleId
          AND um.status = 'ACTIVE'
          AND um.enabled = true
          AND p.status = 'ACTIVE'
    """)
    List<String> findPermissions(
            @Param("userId") UUID userId,
            @Param("moduleId") UUID moduleId
    );

    @Query("""
        SELECT um.module.id
        FROM UserModule um
        WHERE um.user.id = :userId
          AND um.status = 'ACTIVE'
          AND um.enabled = true
    """)
    List<UUID> findActiveModuleIdsByUserId(
            @Param("userId") UUID userId
    );

    boolean existsByModuleId(UUID moduleId);
}