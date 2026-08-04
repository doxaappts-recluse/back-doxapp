package pe.dcs.app.features.report.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class HrCard {

    private long activeStaff;

    private long pendingLeaveRequests;
}
