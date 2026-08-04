package pe.dcs.app.features.bible_academy.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.util.List;

/** Scoping por classId siempre viene del path (ver BibleAcademyController), igual patrón que PastoralFollowUpHistoryRequest. */
@Getter
@Setter
public class BibleEnrollmentSearchRequest {

    private BibleEnrollmentFilterRequest filters;

    private PaginationRequest pagination;

    private List<SortRequest> sorts;
}
