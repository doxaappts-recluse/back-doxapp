package pe.dcs.app.features.visibility.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.rules.VisibilityStatus;

@Getter
@Setter
public class VisibilityRequestFilterRequest {

    private VisibilityStatus status;

    private String moduleCode;
}
