package pe.dcs.app.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.Contract;
import pe.dcs.app.util.enums.contract.ContractStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContractRepository extends JpaRepository<Contract, UUID>, JpaSpecificationExecutor<Contract> {

    @Query("""
        SELECT c
        FROM Contract c
        WHERE c.organization.id = :organizationId
          AND c.status = 'ACTIVE'
          AND CURRENT_DATE BETWEEN c.startDate AND c.endDate
        ORDER BY c.startDate DESC
    """)
    Optional<Contract> findActiveByOrganizationId(UUID organizationId);

    @Query(
            value = """
            SELECT ranked.*
                  FROM (
                      SELECT
                          c.*,
                          o.name AS organization_name,
                          ROW_NUMBER() OVER (
                              PARTITION BY c.organization_id
                              ORDER BY
                                  CASE c.status
                                      WHEN 'ACTIVE' THEN 1
                                      WHEN 'PENDING' THEN 2
                                      WHEN 'SUSPENDED' THEN 3
                                      WHEN 'EXPIRED' THEN 4
                                      WHEN 'CANCELLED' THEN 5
                                      ELSE 999
                                  END,
                                  c.end_date DESC
                          ) rn
                      FROM contracts c
                      JOIN organizations o ON o.id = c.organization_id
                  ) ranked
                  WHERE ranked.rn = 1
            """,
            nativeQuery = true
    )
    Page<Contract> findRepresentativeContracts(Pageable pageable);

    Optional<Contract>
    findTopByOrganizationIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByEndDateDesc(
            UUID organizationId,
            ContractStatus status,
            LocalDate now1,
            LocalDate now2
    );

    List<Contract> findByOrganizationId(UUID organizationId);

    @Query("""
        SELECT c FROM Contract c
        WHERE c.organization.id = :orgId
        AND c.startDate <= :endDate
        AND c.endDate >= :startDate
    """)
    List<Contract> findOverlappingContracts(
            @Param("orgId") UUID orgId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Lock(LockModeType
            .PESSIMISTIC_WRITE)
        @Query("""
        SELECT c FROM Contract c
        WHERE c.organization.id = :orgId
    """)
    List<Contract> lockByOrganization(@Param("orgId") UUID orgId);

    Optional<Contract> findTopByOrganizationIdOrderByEndDateDesc(UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Contract> findById(UUID id);

    Optional<Contract>
    findTopByOrganizationIdAndStatusInOrderByEndDateDesc(
            UUID organizationId,
            List<ContractStatus> statuses
    );

    boolean existsByOrganizationIdAndStatus(
            UUID organizationId,
            ContractStatus status
    );

    List<Contract> findByOrganizationIdAndStatusIn(
            UUID organizationId,
            List<ContractStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
       SELECT c
       FROM Contract c
       WHERE c.id = :id
       """)
    Optional<Contract> findByIdForUpdate(UUID id);

    // =========================================
    // JOBS
    // =========================================

    List<Contract> findByStatusAndEndDateBefore(
            ContractStatus status,
            LocalDate date
    );

    List<Contract> findByStatusAndStartDateLessThanEqual(
            ContractStatus status,
            LocalDate date
    );

    Optional<Contract> findFirstByOrganizationIdAndStatusOrderByStartDateDesc(
            UUID organizationId,
            ContractStatus status
    );

    long countByOrganizationId(UUID organizationId);
}