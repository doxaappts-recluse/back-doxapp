package pe.dcs.app.features.module;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Contract;
import pe.dcs.app.repository.ContractModuleRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractModuleResolver {

    private final ContractResolver contractResolver;
    private final ContractModuleRepository contractModuleRepository;

    @Transactional(readOnly = true)
    public Set<UUID> resolveModules(
            UUID branchId
    ){

        Set<UUID> modules = new HashSet<>();

        List<Contract> contracts =
                contractResolver
                        .getActiveContractsByBranch(
                                branchId
                        );

        contracts.forEach(
                contract ->
                        modules.addAll(
                                contractModuleRepository
                                        .findModuleIdsByContractId(
                                                contract.getId()
                                        )
                        )
        );

        return modules;
    }
}