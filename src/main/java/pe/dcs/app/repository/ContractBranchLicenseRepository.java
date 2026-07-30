package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.ContractBranchLicense;

import java.util.List;
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

}
