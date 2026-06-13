package pe.dcs.app.features.module.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.dcs.app.repository.ContractModulePermissionRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final ContractModulePermissionRepository repo;

    public List<String> getPermissions(UUID contractId, UUID moduleId) {
        return repo.findPermissions(contractId, moduleId);
    }
}