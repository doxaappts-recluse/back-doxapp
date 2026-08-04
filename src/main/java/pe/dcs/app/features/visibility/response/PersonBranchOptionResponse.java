package pe.dcs.app.features.visibility.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Una sede por la que la persona pasó antes (para elegir la
 * "sourceBranch" al armar una solicitud de visibilidad).
 */
@Getter
@Setter
@AllArgsConstructor
public class PersonBranchOptionResponse {

    private UUID branchId;
    private String branchName;
    private LocalDate startDate;
    private LocalDate endDate;
}
