package pe.dcs.app.features.permission.response;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class PermissionResponse {

    private UUID id;
    private String code;
    private String name;
}