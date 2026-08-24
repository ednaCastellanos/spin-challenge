package mx.spin.transactions.application.common;

import java.util.List;

public record PageResult<T>(List<T> content, int page, int size, long totalElements) {

    public PageResult { content = List.copyOf(content); }

    public int totalPages() { return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size); }

    public static <T> PageResult<T> of(List<T> content, int page, int size, long total) {
        return new PageResult<>(content, page, size, total);
    }
}