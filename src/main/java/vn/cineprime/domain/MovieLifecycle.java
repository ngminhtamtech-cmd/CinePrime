package vn.cineprime.domain;

import java.time.*;
import vn.cineprime.domain.Domain.Movie;

/** Release classification is evaluated at read time, independently of sales and visibility. */
public final class MovieLifecycle {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private MovieLifecycle() {}
    public static LocalDate today(Clock clock) { return clock.instant().atZone(ZONE).toLocalDate(); }
    public static String status(Movie movie, LocalDate today) {
        if ("STOPPED".equals(movie.status) || movie.releaseDate == null) return movie.status;
        return movie.releaseDate.isAfter(today) ? "COMING_SOON" : "NOW_SHOWING";
    }
    public static String label(String status) {
        return switch (status) { case "COMING_SOON" -> "Sắp chiếu"; case "NOW_SHOWING" -> "Đang chiếu"; default -> "Ngừng chiếu"; };
    }
    /** Same rule in SQL/HQL, before filtering, counting and pagination. */
    public static String expression(String alias, String dateParameter) {
        return "(case when " + alias + ".status='STOPPED' or " + alias + ".releaseDate is null then " + alias + ".status when " + alias + ".releaseDate>" + dateParameter + " then 'COMING_SOON' else 'NOW_SHOWING' end)";
    }
}
