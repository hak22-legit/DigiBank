package com.bank.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PagedResult<T> {
    private List<T> items;
    private int currentPage;
    private int pageSize;
    private long totalItems;
    private int totalPages;

    public boolean hasNextPage() {
        return currentPage < totalPages;
    }

    public boolean hasPreviousPage() {
        return currentPage > 1;
    }
}