package pe.dcs.app.features.contract.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ContractRepository extends JpaRepository<Contract, UUID>, JpaSpecificationExecutor<Contract> {

    List<Contract> findByOrganizationIdAndStatus(UUID organizationId, ContractStatus status);

    List<Contract> findByOrganizationIdOrderByStartDateDescCreatedAtDesc(UUID organizationId);

    List<Contract> findByPreviousContractId(UUID previousContractId);

    List<Contract> findByStatusIn(Collection<ContractStatus> statuses);

    /** Códigos de módulo incluidos en los contratos ACTIVE de la organización. */
    @Query(value = """
            select distinct cm.module_code
              from contract_module cm
              join contract c on c.id = cm.contract_id
             where c.organization_id = :orgId and c.status = 'ACTIVE'
            """, nativeQuery = true)
    List<String> findActiveModuleCodes(UUID orgId);

    @Query(value = "select count(*) > 0 from contract where organization_id = :orgId and status = 'ACTIVE'",
            nativeQuery = true)
    boolean existsActive(UUID orgId);

    /** Máximo de licencias sumando contratos ACTIVE vigentes (0 si no hay contrato). */
    @Query(value = "select coalesce(max(max_licenses), 0) from contract where organization_id = :orgId and status = 'ACTIVE'",
            nativeQuery = true)
    int activeMaxLicenses(UUID orgId);

    /**
     * [V6] Contratos PENDING/ACTIVE del mismo alcance cuyo periodo se cruza con [start, end]. {@code branchId} null =
     * alcance de organización; con valor, alcance de esa sede. {@code excluded} nunca puede estar vacío (usar un UUID nulo).
     */
    @Query(value = """
            select count(*) from contract c
             where c.organization_id = :orgId
               and c.scope = :scope
               and (c.branch_id is not distinct from cast(:branchId as uuid))
               and c.status in ('PENDING', 'ACTIVE')
               and c.start_date <= :endDate and c.end_date >= :startDate
               and c.id not in (:excluded)
            """, nativeQuery = true)
    long countOverlapping(UUID orgId, String scope, UUID branchId, LocalDate startDate, LocalDate endDate,
                          Collection<UUID> excluded);

    /** Cantidad de contratos ACTIVE (de cualquier organización) que incluyen el módulo. */
    @Query(value = """
            select count(distinct c.id) from contract c join contract_module cm on cm.contract_id = c.id
             where c.status = 'ACTIVE' and cm.module_code = :code
            """, nativeQuery = true)
    long countActiveWithModule(String code);

    /** Contratos (en cualquier estado) que referencian el módulo. */
    @Query(value = "select count(*) from contract_module where module_code = :code", nativeQuery = true)
    long countReferencing(String code);

    // ---- uso de licencias (accesos administrativos ACTIVE; MEMBER no consume)

    @Query(value = """
            select count(*) from user_access
             where organization_id = :orgId and status = 'ACTIVE'
               and role in ('ORG_ADMIN', 'ORG_BRANCH_ADMIN', 'ORG_USER')
            """, nativeQuery = true)
    long licensesUsedInOrganization(UUID orgId);

    @Query(value = """
            select count(*) from user_access
             where organization_id = :orgId and branch_id = :branchId and status = 'ACTIVE'
               and role in ('ORG_ADMIN', 'ORG_BRANCH_ADMIN', 'ORG_USER')
            """, nativeQuery = true)
    long licensesUsedInBranch(UUID orgId, UUID branchId);

    /** Uso por sede: filas (branch_id, cantidad). Los ORG_ADMIN (sin sede) no aparecen aquí. */
    @Query(value = """
            select cast(branch_id as varchar), count(*) from user_access
             where organization_id = :orgId and branch_id is not null and status = 'ACTIVE'
               and role in ('ORG_ADMIN', 'ORG_BRANCH_ADMIN', 'ORG_USER')
             group by branch_id
            """, nativeQuery = true)
    List<Object[]> licensesUsedByBranch(UUID orgId);

    @Query(value = "select count(*) from branch where organization_id = :orgId and status = 'ACTIVE'", nativeQuery = true)
    long activeBranchCount(UUID orgId);

    /** Correos de los administradores activos de la organización (destinatarios de los avisos). */
    @Query(value = """
            select distinct p.email from user_access ua
              join person p on p.id = ua.person_id and p.organization_id = ua.organization_id
             where ua.organization_id = :orgId and ua.role = 'ORG_ADMIN' and ua.status = 'ACTIVE' and p.email is not null
            """, nativeQuery = true)
    List<String> adminEmails(UUID orgId);
}
