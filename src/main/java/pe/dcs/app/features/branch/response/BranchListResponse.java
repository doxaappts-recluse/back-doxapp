package pe.dcs.app.features.branch.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class BranchListResponse {
    private UUID id;
    private String name;
}