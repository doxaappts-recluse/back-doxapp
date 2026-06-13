package pe.dcs.app.features.module.response;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ModuleSimpleResponse {

    private UUID id;
    private String name;
    private String code;
}