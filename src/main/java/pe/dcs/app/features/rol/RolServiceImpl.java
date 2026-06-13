package pe.dcs.app.features.rol;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.repository.RoleRepository;
import pe.dcs.app.features.rol.response.RoleResponse;
import pe.dcs.app.features.rol.service.RolService;
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

    public List<RoleResponse> getSystemRoles() {
        return roleRepository.findByPrefixAndStatus("SYSTEM_", StatusType.ACTIVE);
    }

    public List<RoleResponse> getOrganizationRoles() {
        return roleRepository.findByPrefixAndStatus("ORG_", StatusType.ACTIVE);
    }
}