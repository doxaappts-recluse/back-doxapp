package pe.dcs.app.features.event.response.registration;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class EventPersonSearchResponse {

    private UUID id;

    private String name;

    private String lastname;

    private String phone;

    private LocalDate dateBirth;
}
