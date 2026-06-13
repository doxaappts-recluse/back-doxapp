package pe.dcs.app.features.organization.mapper;

import pe.dcs.app.entity.Organization;

import pe.dcs.app.features.organization.response.OrganizationResponse;
import pe.dcs.app.util.enums.StatusType;

public class OrganizationMapper {

    private OrganizationMapper() {
    }

    public static OrganizationResponse toResponse(
            Organization organization
    ) {

        return OrganizationResponse.builder()
                .id(organization.getId())
                .name(organization.getName())
                .address(organization.getAddress())
                .ruc(organization.getRuc())
                .status(organization.getStatus() == StatusType.ACTIVE)
                .build();
    }
}