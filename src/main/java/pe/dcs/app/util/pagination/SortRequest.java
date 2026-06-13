package pe.dcs.app.util.pagination;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SortRequest {

    private String field;

    private String key;

    private String direction;

    public String getResolvedField() {

        if (key != null && !key.isBlank()) {
            return key;
        }

        return field;
    }
}