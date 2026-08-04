package pe.dcs.app.features.branch_transfer.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Fila del listado de personas con su sede actual.
 */
@Getter
@Setter
public class BranchTransferSearchRowResponse {

    private UUID id;

    private String name;

    private String lastname;

    private String currentBranchName;

    private LocalDate currentBranchStartDate;
}
