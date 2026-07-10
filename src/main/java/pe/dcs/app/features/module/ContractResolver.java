package pe.dcs.app.features.module;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Contract;
import pe.dcs.app.repository.ContractRepository;
import pe.dcs.app.util.Exceptions;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractResolver {

    private final ContractRepository contractRepository;

    /**
     * Obtiene el contrato activo
     * asociado a una sede.
     */
    @Transactional(readOnly = true)
    public Contract getActiveContract(
            UUID branchId
    ){

        if(branchId == null){
            return null;
        }

        return contractRepository
                .findActiveByBranchId(
                        branchId
                )
                .orElse(null);
    }

    /**
     * Valida si una sede
     * tiene contrato activo.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveContract(
            UUID branchId
    ){

        if(branchId == null){
            return false;
        }

        return contractRepository
                .existsActiveByBranchId(
                        branchId
                );
    }

    /**
     * Obtiene contrato activo
     * o genera error.
     */
    @Transactional(readOnly = true)
    public Contract requireActiveContract(
            UUID branchId
    ){

        if(branchId == null){
            throw new Exceptions(
                    "Branch is required",
                    HttpStatus.FORBIDDEN
            );
        }

        return contractRepository
                .findActiveByBranchId(
                        branchId
                )
                .orElseThrow(
                        () ->
                                new Exceptions(
                                        "Branch has no active contract",
                                        HttpStatus.FORBIDDEN
                                )
                );
    }

}