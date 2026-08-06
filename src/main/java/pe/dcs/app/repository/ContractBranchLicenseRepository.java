package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.ContractBranchLicense;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContractBranchLicenseRepository extends JpaRepository<ContractBranchLicense, UUID> {

    @Query("""
        SELECT cbl
        FROM ContractBranchLicense cbl
        JOIN FETCH cbl.branch b
        WHERE cbl.contract.id = :contractId
        ORDER BY b.name ASC
    """)
    List<ContractBranchLicense> findByContractId(
            @Param("contractId") UUID contractId
    );

    void deleteByContractId(
            UUID contractId
    );

    @Query("""
        SELECT COALESCE(SUM(cbl.allocatedLicenses), 0)
        FROM ContractBranchLicense cbl
        WHERE cbl.contract.id = :contractId
    """)
    Integer sumAllocatedByContractId(
            @Param("contractId") UUID contractId
    );

    /**
     * Licencias asignadas a UNA sede puntual dentro de un
     * contrato con distributionMode=ALLOCATED. Si la sede no
     * tiene fila (no se le asignó cupo explícito), se considera
     * 0 — ver LicenseGuard.
     */
    @Query("""
        SELECT cbl.allocatedLicenses
        FROM ContractBranchLicense cbl
        WHERE cbl.contract.id = :contractId
          AND cbl.branch.id = :branchId
    """)
    Optional<Integer> findAllocatedLicenses(
            @Param("contractId") UUID contractId,
            @Param("branchId") UUID branchId
    );

}
