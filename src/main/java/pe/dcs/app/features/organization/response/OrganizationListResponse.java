package pe.dcs.app.features.organization.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class OrganizationListResponse {
    private UUID id;
    private String name;
}