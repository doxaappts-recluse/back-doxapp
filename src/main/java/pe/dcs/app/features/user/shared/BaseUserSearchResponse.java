package pe.dcs.app.features.user.shared;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class BaseUserSearchResponse {
    private UUID id;
    private String name;
    private String lastname;
}