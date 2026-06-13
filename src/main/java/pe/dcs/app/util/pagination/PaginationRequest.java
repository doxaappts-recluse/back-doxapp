package pe.dcs.app.util.pagination;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaginationRequest {

    private int page = 0;
    private int size = 10;
}