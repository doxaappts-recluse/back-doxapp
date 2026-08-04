package pe.dcs.app.features.event.response.registration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Detalle de una fila del lote que NO se pudo registrar (p.ej.
 * persona ya inscrita). bulkCreate() ya no aborta todo el lote por
 * una fila inválida: guarda las válidas y reporta acá cuáles
 * fallaron y por qué, para que el front las deje visibles en el
 * borrador y el usuario las corrija o las quite.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EventRegistrationBulkErrorResponse {

    private Integer index;

    private String name;

    private String lastname;

    private String message;
}
