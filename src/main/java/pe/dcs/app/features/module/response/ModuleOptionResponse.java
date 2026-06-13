package pe.dcs.app.features.module.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
public class ModuleOptionResponse {
    private UUID id;
    private String name;
}
