package pe.dcs.app.features.visitor.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;
import pe.dcs.app.util.enums.visitor.VisitorConsolidationStage;
import pe.dcs.app.util.enums.visitor.VisitorHowArrived;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class VisitorSearchRowResponse extends AuditableResponse {

    private UUID id;

    private UUID personId;
    private String personName;
    private String personLastname;
    private String personDni;

    private LocalDate firstVisitDate;

    private VisitorHowArrived howArrived;

    private VisitorConsolidationStage consolidationStage;

    private LocalDate convertedAt;

    private UUID branchId;
    private String branchName;
}
