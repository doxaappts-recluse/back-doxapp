package pe.dcs.app.features.dashboard.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
public class NextEventResponse {

    private UUID id;
    private String name;
    private LocalDateTime startDateTime;
}
