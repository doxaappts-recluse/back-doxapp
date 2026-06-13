package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.ContractModule;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContractModuleRepository extends JpaRepository<ContractModule, UUID> {

    // =========================================
    // FIND MODULE BY CONTRACT + MODULE CODE
    // =========================================
    @Query("""
        SELECT cm
        FROM ContractModule cm
        JOIN cm.module m
        WHERE cm.contract.id = :contractId
          AND m.code = :moduleCode
          AND cm.status = 'ACTIVE'
    """)
    Optional<ContractModule> findByContractIdAndModuleCode(
            @Param("contractId") UUID contractId,
            @Param("moduleCode") String moduleCode
    );

    @Query("""
        SELECT cm.module.id
        FROM ContractModule cm
        WHERE cm.contract.id = :contractId
          AND cm.status = 'ACTIVE'
    """)
    List<UUID> findModuleIdsByContractId(UUID contractId);

    List<ContractModule> findByContractId(UUID contractId);

    List<ContractModule> findByContractIdAndStatus(
            UUID contractId,
            StatusType status
    );

}