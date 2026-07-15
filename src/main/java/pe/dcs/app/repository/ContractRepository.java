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

    // =========================================================
    // CONTRATO ACTIVO POR SEDE
    // =========================================================

    @Query("""
        SELECT c
        FROM Contract c
        WHERE c.branch.id = :branchId
          AND c.scope = 'BRANCH'
          AND c.status = 'ACTIVE'
          AND CURRENT_DATE BETWEEN c.startDate AND c.endDate
        ORDER BY c.startDate DESC
    """)
    List<Contract> findActiveByBranchId(
            @Param("branchId") UUID branchId
    );

    @Query("""
    SELECT c
        FROM Contract c
        WHERE c.status = 'ACTIVE'
          AND CURRENT_DATE BETWEEN c.startDate AND c.endDate
          AND
          (
              (
                  c.scope = 'BRANCH'
                  AND c.branch.id = :branchId
              )
              OR
              (
                  c.scope = 'ORGANIZATION'
                  AND c.organization.id =
                      (
                          SELECT b.organization.id
                          FROM Branch b
                          WHERE b.id = :branchId
                      )
              )
          )
        ORDER BY c.startDate DESC
    """)
    List<Contract> findActiveContractsForBranch(
            @Param("branchId") UUID branchId
    );

    // =========================================================
    // CONTRATO ACTIVO POR ORG
    // =========================================================
    @Query("""
    SELECT c
        FROM Contract c
        WHERE c.organization.id = :organizationId
          AND c.scope = 'ORGANIZATION'
          AND c.status = 'ACTIVE'
          AND CURRENT_DATE BETWEEN c.startDate AND c.endDate
        ORDER BY c.startDate DESC
    """)
    List<Contract> findActiveByOrganizationId(
            @Param("organizationId") UUID organizationId
    );

    // =========================================================
    // HISTORIAL DE CONTRATOS DE UNA SEDE
    // =========================================================

    List<Contract> findByBranchId(
            UUID branchId
    );

    // =========================================================
    // CONTRATOS POR ESTADO
    // =========================================================

    List<Contract> findByBranchIdAndStatusIn(
            UUID branchId,
            List<ContractStatus> statuses
    );

    Optional<Contract>
    findTopByBranchIdOrderByEndDateDesc(
            UUID branchId
    );

    Optional<Contract>
    findTopByBranchIdAndStatusOrderByEndDateDesc(
            UUID branchId,
            ContractStatus status
    );

    // =========================================================
    // VALIDAR SOLAPAMIENTO
    // =========================================================

    @Query("""
        SELECT c
        FROM Contract c
        WHERE c.branch.id = :branchId
          AND c.startDate <= :endDate
          AND c.endDate >= :startDate
    """)
    List<Contract> findOverlappingContracts(
            @Param("branchId") UUID branchId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // =========================================================
    // LOCK PARA OPERACIONES CRITICAS
    // =========================================================

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT c
        FROM Contract c
        WHERE c.branch.id = :branchId
    """)
    List<Contract> lockByBranch(
            @Param("branchId") UUID branchId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT c
        FROM Contract c
        WHERE c.id = :id
    """)
    Optional<Contract> findByIdForUpdate(
            @Param("id") UUID id
    );

    // =========================================================
    // JOBS
    // =========================================================

    List<Contract> findByStatusAndEndDateBefore(
            ContractStatus status,
            LocalDate date
    );

    List<Contract> findByStatusAndStartDateLessThanEqual(
            ContractStatus status,
            LocalDate date
    );

    // =========================================================
    // ADMINISTRACION
    // =========================================================

    long countByBranchId(
            UUID branchId
    );

    @Query("""
    SELECT COUNT(c) > 0
        FROM Contract c
        WHERE c.status = 'ACTIVE'
        AND CURRENT_DATE BETWEEN c.startDate AND c.endDate
        AND
        (
            (
                c.scope = 'BRANCH'
                AND c.branch.id = :branchId
            )
            OR
            (
                c.scope = 'ORGANIZATION'
                AND c.organization.id =
                    (
                        SELECT b.organization.id
                        FROM Branch b
                        WHERE b.id = :branchId
                    )
            )
        )
    """)
    boolean existsActiveByBranchId(
            @Param("branchId") UUID branchId
    );

    boolean existsByBranchOrganizationIdAndStatusAndEndDateGreaterThanEqual(
            UUID organizationId,
            ContractStatus status,
            LocalDate endDate
    );

    boolean existsByBranchIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            UUID branchId,
            ContractStatus status,
            LocalDate startDate,
            LocalDate endDate
    );

}