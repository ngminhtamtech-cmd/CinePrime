package vn.cineprime.admin;

import java.util.*;

/** A single, fixed Admin page size. Totals always describe the entire filtered scope. */
public record AdminPage<T>(List<T> rows, long total, int page, boolean hasPrevious, boolean hasMore) {
    public static final int SIZE = 5;
    public AdminPage { rows = List.copyOf(rows); }
    public static int number(int requested, long total) {
        return (int)Math.min(Math.max(0, requested), Math.max(0, (total - 1) / SIZE));
    }
    public static <T> AdminPage<T> slice(List<T> source, int requested) {
        int page = number(requested, source.size()), start = page * SIZE;
        return new AdminPage<>(source.subList(start, Math.min(start + SIZE, source.size())), source.size(), page, page > 0, start + SIZE < source.size());
    }
    public Map<String,Object> model() {
        return Map.of("rows",rows,"total",total,"page",page,"hasPrevious",hasPrevious,"hasMore",hasMore);
    }
}
