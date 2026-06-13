package pe.dcs.app.features.event.response.registration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EventRegistrationBulkResponse {

    private Integer totalProcessed;

    private List<EventRegistrationResponse> registrations;
}