package pe.dcs.app.security.service;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OrganizationContext {

    private final ThreadLocal<UUID> organization =
            new ThreadLocal<>();


    private final ThreadLocal<UUID> branch =
            new ThreadLocal<>();

    public void set(
            UUID organizationId,
            UUID branchId
    ){
        organization.set(organizationId);
        branch.set(branchId);
    }

    public UUID getOrganizationId(){
        return organization.get();
    }

    public UUID getBranchId(){
        return branch.get();
    }

    public void clear(){
        organization.remove();
        branch.remove();
    }

}