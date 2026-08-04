package pe.dcs.app.features.visibility.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
public class VisibilityRequestPersonResponse {

    private UUID personId;
    private String name;
    private String lastname;
}
