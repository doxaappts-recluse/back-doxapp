package pe.dcs.app.features.contract.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ContractExpiryNoticeRepository extends JpaRepository<ContractExpiryNotice, ContractExpiryNotice.Key> {

    List<ContractExpiryNotice> findByContractId(UUID contractId);
}
