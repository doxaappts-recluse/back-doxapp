package pe.dcs.app.features.contract;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Contract;
import pe.dcs.app.repository.ContractRepository;
import pe.dcs.app.util.enums.contract.ContractStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContractScheduler {

    private final ContractRepository contractRepository;

    @Scheduled(
            cron = "0 0 0 * * *",
            zone = "America/Lima"
    )
    @Transactional
    public void processContracts() {

        LocalDate today = LocalDate.now();

        expireContracts(today);

        activatePendingContracts(today);
    }

    // =====================================================
    // EXPIRE
    // =====================================================

    private void expireContracts(LocalDate today) {

        List<Contract> contracts =
                contractRepository.findByStatusAndEndDateBefore(
                        ContractStatus.ACTIVE,
                        today
                );

        if (contracts.isEmpty()) {
            return;
        }

        contracts.forEach(Contract::expire);

        contractRepository.saveAll(contracts);

        log.info(
                "Expired {} contracts",
                contracts.size()
        );
    }

    // =====================================================
    // ACTIVATE PENDING
    // =====================================================

    private void activatePendingContracts(LocalDate today) {

        List<Contract> pendingContracts =
                contractRepository
                        .findByStatusAndStartDateLessThanEqual(
                                ContractStatus.PENDING,
                                today
                        );

        if (pendingContracts.isEmpty()) {
            return;
        }

        List<Contract> activated = new ArrayList<>();

        for (Contract contract : pendingContracts) {

            UUID organizationId =
                    contract.getOrganization().getId();

            boolean hasActive =
                    contractRepository
                            .existsByOrganizationIdAndStatus(
                                    organizationId,
                                    ContractStatus.ACTIVE
                            );

            // evita dos activos
            if (hasActive) {
                continue;
            }

            try {

                contract.activateManually();

                activated.add(contract);

            } catch (Exception ex) {

                log.warn(
                        "Could not activate contract {}: {}",
                        contract.getId(),
                        ex.getMessage()
                );
            }
        }

        if (!activated.isEmpty()) {
            contractRepository.saveAll(activated);

            log.info(
                    "Activated {} contracts",
                    activated.size()
            );
        }
    }
}