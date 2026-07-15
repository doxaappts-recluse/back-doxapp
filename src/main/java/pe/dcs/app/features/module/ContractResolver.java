package pe.dcs.app.features.module;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Contract;
import pe.dcs.app.repository.ContractRepository;
import pe.dcs.app.util.Exceptions;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractResolver {

    private final ContractRepository contractRepository;

    @Transactional(readOnly = true)
    public List<Contract> getActiveContractsByBranch(UUID branchId){

        if(branchId == null){
            return List.of();
        }

        return contractRepository.findActiveContractsForBranch(branchId);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveContract(UUID branchId){
        return !getActiveContractsByBranch(branchId)
                .isEmpty();
    }
}