package pe.dcs.app.features.report.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class VisitorCard {

    private long totalVisitors;

    /** consolidationStage IN (NEW, IN_FOLLOWUP, INTEGRATED). */
    private long inConsolidation;

    /** consolidationStage = CONVERTED. */
    private long converted;
}
