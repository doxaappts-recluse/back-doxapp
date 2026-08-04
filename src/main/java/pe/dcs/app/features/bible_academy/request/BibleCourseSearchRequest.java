package pe.dcs.app.features.bible_academy.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.util.List;

@Getter
@Setter
public class BibleCourseSearchRequest {

    private BibleCourseFilterRequest filters;

    private PaginationRequest pagination;

    private List<SortRequest> sorts;
}
