package mx.spin.transactions.adapter.in.rest.dto;

import java.util.List;

public record PagedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) { }