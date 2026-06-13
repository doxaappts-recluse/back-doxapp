package pe.dcs.app.util.pagination;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaginationResponse {

    //Atributos
    private int totalRecords;
    private int totalPages;
    private int sizePages;
    private int currentPage;

    //Constructores
    public PaginationResponse(int totalRecords, int totalPages, int sizePages, int currentPage) {
        this.totalRecords = totalRecords;
        this.totalPages = totalPages;
        this.sizePages = sizePages;
        this.currentPage = currentPage;
    }
}
