package pe.dcs.app.features.contract;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.contract.mapper.ContractModuleMapper;
import pe.dcs.app.features.contract.service.ContractModuleService;
import pe.dcs.app.entity.Contract;
import pe.dcs.app.entity.ContractModule;
import pe.dcs.app.entity.ContractModulePermission;
import pe.dcs.app.entity.Module;
import pe.dcs.app.entity.Permission;
import pe.dcs.app.features.contract.request.ContractModuleRequest;
import pe.dcs.app.features.contract.response.ContractModuleResponse;
import pe.dcs.app.features.permission.response.PermissionResponse;
import pe.dcs.app.repository.ContractModulePermissionRepository;
import pe.dcs.app.repository.ContractModuleRepository;
import pe.dcs.app.repository.ModuleRepository;
import pe.dcs.app.repository.PermissionRepository;
import pe.dcs.app.features.permission.mapper.PermissionMapper;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ContractModuleServiceImpl
        implements ContractModuleService {

    private final ModuleRepository moduleRepository;

    private final PermissionRepository permissionRepository;

    private final ContractModuleRepository contractModuleRepository;

    private final ContractModulePermissionRepository
            contractModulePermissionRepository;

    @Override
    public List<ContractModuleResponse> saveModules(
            Contract contract,
            List<ContractModuleRequest> requests
    ) {

        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        List<ContractModuleResponse> response =
                new ArrayList<>();

        for (ContractModuleRequest req : requests) {

            Module module = moduleRepository
                    .findById(req.getModuleId())
                    .orElseThrow(() ->
                            new Exceptions(
                                    "Module not found",
                                    HttpStatus.NOT_FOUND
                            )
                    );

            ContractModule contractModule =
                    new ContractModule();

            contractModule.setContract(contract);

            contractModule.setModule(module);

            contractModule.setStatus(StatusType.ACTIVE);

            contractModule.setEnabledAt(
                    LocalDateTime.now()
            );

            contractModuleRepository.save(contractModule);

            List<PermissionResponse> permissions =
                    savePermissions(
                            contractModule,
                            req.getPermissionIds()
                    );

            response.add(
                    ContractModuleMapper.toResponse(
                            contractModule,
                            permissions
                    )
            );
        }

        return response;
    }

    @Override
    public List<ContractModuleResponse> getModules(
            UUID contractId
    ) {

        List<ContractModule> modules =
                contractModuleRepository
                        .findByContractId(contractId);

        return modules.stream()
                .map(cm -> {

                    List<PermissionResponse> permissions =
                            contractModulePermissionRepository
                                    .findByContractModuleId(
                                            cm.getId()
                                    )
                                    .stream()
                                    .map(x ->
                                            PermissionMapper.toResponse(
                                                    x.getPermission()
                                            )
                                    )
                                    .toList();

                    return ContractModuleMapper.toResponse(
                            cm,
                            permissions
                    );
                })
                .toList();
    }

    private List<PermissionResponse> savePermissions(
            ContractModule contractModule,
            List<UUID> permissionIds
    ) {

        if (permissionIds == null ||
                permissionIds.isEmpty()) {

            return List.of();
        }

        List<PermissionResponse> response =
                new ArrayList<>();

        List<Permission> permissions =
                permissionRepository.findAllById(
                        permissionIds
                );

        for (Permission permission : permissions) {

            ContractModulePermission cmp =
                    new ContractModulePermission();

            cmp.setContractModule(contractModule);

            cmp.setPermission(permission);

            contractModulePermissionRepository
                    .save(cmp);

            response.add(
                    PermissionMapper.toResponse(
                            permission
                    )
            );
        }

        return response;
    }
}