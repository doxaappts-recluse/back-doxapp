package pe.dcs.app.features.event.response.attendance;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
public class CheckInResponse {
    private UUID registrationId;
    private String name;
    private String lastname;
    private LocalDateTime attendedAt;
}