package pe.dcs.app.features.contract;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Contract;
import pe.dcs.app.repository.ContractRepository;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.contract.ContractStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Corre una vez al día: vence contratos cuya fecha fin ya
 * pasó, y activa los PENDING cuya fecha inicio ya llegó.
 *
 * No hace falta revalidar solapamiento acá: eso ya se
 * garantiza al crear/editar un contrato (validateNoOverlap
 * en ContractServiceImpl), así que dos contratos ACTIVE del
 * mismo alcance (misma sede, o misma organización) nunca
 * deberían coexistir en la misma fecha.
 */
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

        /*
         * Activar ANTES de vencer: una renovación a futuro deja el
         * contrato viejo ACTIVE (sin tocar) hasta que a su sucesor
         * le toque arrancar. Si hoy es ese día, hay que activar el
         * nuevo y marcar el viejo como REPLACED (no EXPIRED) antes
         * de que el paso de "vencer" siquiera lo vea, para que
         * quede reflejado como una transición de versión y no como
         * un vencimiento común.
         */
        activatePendingContracts(today);

        expireContracts(today);
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
        List<Contract> replacedPredecessors = new ArrayList<>();

        for (Contract contract : pendingContracts) {

            try {

                contract.activate();

                activated.add(contract);

                /*
                 * Este PENDING puede venir de una renovación
                 * declarada a futuro (ver ContractServiceImpl.
                 * tryReplaceWithNewVersion): en ese caso el
                 * contrato anterior se dejó ACTIVE a propósito,
                 * sin tocar, para que siguiera editable hasta hoy.
                 * Ahora que el sucesor de verdad arranca, recién
                 * corresponde cerrar el anterior como REPLACED.
                 */
                Contract previous = contract.getPreviousContract();

                if (previous != null
                        && previous.getStatus() != ContractStatus.CANCELLED
                        && previous.getStatus() != ContractStatus.EXPIRED
                        && previous.getStatus() != ContractStatus.REPLACED) {

                    previous.markReplaced();

                    replacedPredecessors.add(previous);
                }

            } catch (Exceptions ex) {

                /*
                 * Contract.activate() ahora también exige que hoy
                 * esté dentro de [startDate, endDate] (ver
                 * assertWithinValidityRange). Un PENDING encontrado
                 * acá cumple startDate<=today por la query, pero si
                 * endDate ya pasó (dato corrido/backdated) igual
                 * lanza acá — se loguea y se salta, sin abortar el
                 * resto del batch.
                 */
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

        if (!replacedPredecessors.isEmpty()) {

            contractRepository.saveAll(replacedPredecessors);

            /*
             * Flush explícito: expireContracts() corre justo
             * después dentro de la misma transacción y consulta
             * por status=ACTIVE; sin este flush, un predecesor
             * recién marcado REPLACED podría seguir viéndose como
             * ACTIVE en esa consulta (misma lección que el fix de
             * ContractModuleServiceImpl.replaceModules).
             */
            contractRepository.flush();

            log.info(
                    "Replaced {} predecessor contracts",
                    replacedPredecessors.size()
            );
        }
    }
}
