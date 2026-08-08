package com.spamfilter.adminclient.model;

import java.util.List;

/**
 * A LIMIT/OFFSET page of results plus enough to render pagination controls.
 */
public class Page<T> {

    private final List<T> items;
    private final int page;
    private final int pageSize;
    private final long totalItems;

    public Page(List<T> items, int page, int pageSize, long totalItems) {
        this.items = items;
        this.page = page;
        this.pageSize = pageSize;
        this.totalItems = totalItems;
    }

    public List<T> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public long getTotalItems() {
        return totalItems;
    }

    public int getTotalPages() {
        return (int) Math.max(1, Math.ceil(totalItems / (double) pageSize));
    }
}
