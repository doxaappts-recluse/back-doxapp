package pe.dcs.app.features.user.shared;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BaseUserFilterRequest {
    private String name;
    private String lastname;
}