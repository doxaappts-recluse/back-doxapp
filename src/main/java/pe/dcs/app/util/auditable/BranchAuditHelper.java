package pe.dcs.app.util.auditable;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.Organization;
import pe.dcs.app.repository.BranchRepository;
import pe.dcs.app.repository.OrganizationRepository;
import pe.dcs.app.security.service.AuthContext;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BranchAuditHelper {

    private final OrganizationRepository organizationRepository;
    private final BranchRepository branchRepository;
    private final AuthContext authContext;

    public void apply(BranchAuditable entity, UUID organizationId, UUID branchId){

        if(organizationId == null){
            organizationId = authContext.getCurrentOrganizationId();
        }

        if(branchId == null){
            branchId = authContext.getCurrentBranchId();
        }

        if(organizationId == null || branchId == null){
            throw new IllegalStateException(
                    "Debe existir organización y sede"
            );
        }

        Organization organization = organizationRepository.getReferenceById(organizationId);

        Branch branch = branchRepository.getReferenceById(branchId);

        if(!branch.getOrganization().getId().equals(organizationId)){
            throw new IllegalArgumentException(
                    "La sede no pertenece a la organización"
            );
        }

        entity.setOrganization(organization);
        entity.setBranch(branch);
    }

}