package pe.dcs.app.features.rol;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.repository.RoleRepository;
import pe.dcs.app.features.rol.response.RoleResponse;
import pe.dcs.app.features.rol.service.RolService;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RolServiceImpl implements RolService {

    private final RoleRepository roleRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> getAll() {
        return roleRepository.findByStatus(StatusType.ACTIVE)
                .stream()
                .map(RoleResponse::new)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> getSystemRoles() {

        return roleRepository.findByValueInAndStatus(
                        List.of(
                                RoleType.SYSTEM_ADMIN,
                                RoleType.SYSTEM_SUPPORT
                        ),
                        StatusType.ACTIVE
                )
                .stream()
                .map(RoleResponse::new)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> getOrganizationRoles() {

        return roleRepository.findByValueInAndStatus(
                        List.of(
                                RoleType.ORG_ADMIN,
                                RoleType.ORG_USER,
                                RoleType.ORG_BRANCH_ADMIN
                        ),
                        StatusType.ACTIVE
                )
                .stream()
                .map(RoleResponse::new)
                .toList();
    }
}