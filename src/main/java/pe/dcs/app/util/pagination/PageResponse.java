package pe.dcs.app.util.pagination;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PageResponse<T> {

    private List<T> content;
    private PaginationResponse pagination;

    public PageResponse(List<T> content,
                        PaginationResponse pagination) {
        this.content = content;
        this.pagination = pagination;
    }
}