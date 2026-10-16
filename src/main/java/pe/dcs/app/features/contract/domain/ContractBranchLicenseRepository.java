package pe.dcs.app.features.contract.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ContractBranchLicenseRepository extends JpaRepository<ContractBranchLicense, UUID> {

    List<ContractBranchLicense> findByContractId(UUID contractId);

    List<ContractBranchLicense> findByContractIdIn(Collection<UUID> contractIds);

    /** Borrado masivo inmediato (evita el orden insert-antes-de-delete de Hibernate al reemplazar el reparto). */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from ContractBranchLicense l where l.contractId = :contractId")
    void deleteByContractId(UUID contractId);
}
