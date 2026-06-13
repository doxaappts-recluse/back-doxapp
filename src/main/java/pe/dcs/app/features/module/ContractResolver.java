package pe.dcs.app.features.module;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.dcs.app.entity.Contract;
import pe.dcs.app.repository.ContractRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractResolver {

    private final ContractRepository contractRepository;

    public Contract getActiveContract(UUID organizationId) {

        if (organizationId == null) return null;

        return contractRepository.findActiveByOrganizationId(organizationId)
                .orElse(null);
    }
}