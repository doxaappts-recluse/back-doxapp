package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.FinancialFund;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

@Repository
public interface FinancialFundRepository extends JpaRepository<FinancialFund, UUID> {

    List<FinancialFund> findByOrganizationIdOrderByNameAsc(UUID organizationId);

    List<FinancialFund> findByOrganizationIdAndStatusOrderByNameAsc(
            UUID organizationId,
            StatusType status
    );
}
