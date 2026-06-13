package pe.dcs.app.util.pagination;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import pe.dcs.app.util.enums.contract.ContractSort;

import java.util.List;

public class PageableUtil {

    private PageableUtil() {
    }

    public static Pageable buildPageable(
            PaginationRequest pagination,
            List<SortRequest> sorts
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

                    String resolved = ContractSort
                            .resolvePath(sort.getResolvedField());

                    String field = resolved != null
                            ? resolved
                            : sort.getResolvedField();

                    Sort.Direction direction =
                            "DESC".equalsIgnoreCase(sort.getDirection())
                                    ? Sort.Direction.DESC
                                    : Sort.Direction.ASC;

                    return new Sort.Order(direction, field);
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