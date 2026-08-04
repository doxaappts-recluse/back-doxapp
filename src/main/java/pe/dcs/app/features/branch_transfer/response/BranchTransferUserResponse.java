package pe.dcs.app.features.branch_transfer.response;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class BranchTransferUserResponse {

    private UUID id;

    private String name;

    private String lastname;

    private UUID organizationId;
}
