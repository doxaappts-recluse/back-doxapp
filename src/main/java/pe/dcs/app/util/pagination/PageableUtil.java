package pe.dcs.app.util.pagination;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.function.Function;

public class PageableUtil {

    private PageableUtil() {
    }

    public static Pageable buildPageable(
            PaginationRequest pagination,
            List<SortRequest> sorts
    ) {
        return buildPageable(
                pagination,
                sorts,
                null
        );
    }

    public static Pageable buildPageable(
            PaginationRequest pagination,
            List<SortRequest> sorts,
            Function<String, String> sortResolver
    ) {

        if (pagination == null) {
            pagination = new PaginationRequest();
            pagination.setPage(0);
            pagination.setSize(10);
        }

        if (sorts == null || sorts.isEmpty()) {
            return PageRequest.of(
                    pagination.getPage(),
                    pagination.getSize()
            );
        }


        List<Sort.Order> orders = sorts.stream()

                .filter(sort ->
                        sort.getResolvedField() != null &&
                                !sort.getResolvedField().isBlank()
                )

                .map(sort -> {

                    String field = sort.getResolvedField();

                    if (sortResolver != null) {

                        String resolved = sortResolver.apply(field);

                        if (resolved != null && !resolved.isBlank()) {
                            field = resolved;
                        }
                    }


                    Sort.Direction direction =
                            "DESC".equalsIgnoreCase(sort.getDirection())
                                    ? Sort.Direction.DESC
                                    : Sort.Direction.ASC;


                    return new Sort.Order(
                            direction,
                            field
                    );
                })
                .toList();


        if (orders.isEmpty()) {
            return PageRequest.of(
                    pagination.getPage(),
                    pagination.getSize()
            );
        }


        return PageRequest.of(
                pagination.getPage(),
                pagination.getSize(),
                Sort.by(orders)
        );
    }
}