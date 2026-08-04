package pe.dcs.app.features.branch_transfer.request;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class BranchTransferRequest {

    private UUID targetBranchId;

    private String reason;
}
