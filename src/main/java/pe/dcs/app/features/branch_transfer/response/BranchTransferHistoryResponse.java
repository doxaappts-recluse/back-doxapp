package pe.dcs.app.features.branch_transfer.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Un registro puntual de PersonBranch: se usa tanto para la
 * sede actual como para cada fila del historial.
 */
@Getter
@Setter
public class BranchTransferHistoryResponse {

    private UUID id;

    private UUID branchId;

    private String branchName;

    private String status;

    private LocalDate startDate;

    private LocalDate endDate;

    private String transferReason;
}
