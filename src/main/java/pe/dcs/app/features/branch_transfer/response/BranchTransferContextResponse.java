package pe.dcs.app.features.branch_transfer.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BranchTransferContextResponse {

    private BranchTransferUserResponse user;

    /**
     * Sede actual (status=ACTIVE). Null si la persona no tiene
     * ninguna sede activa (no debería pasar en la práctica).
     */
    private BranchTransferHistoryResponse currentBranch;
}
