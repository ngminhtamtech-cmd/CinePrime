package vn.cineprime.domain;

import java.time.*;
import java.time.format.DateTimeFormatter;
import vn.cineprime.domain.Domain.Movie;

/**
 * Release window [releaseDate, endDate] in Vietnam dates, evaluated at read time.
 * Sắp chiếu before the window, Đang chiếu inside it, Đã kết thúc after it. An ended movie stays listed
 * (dimmed, not bookable) for {@link #LISTED_AFTER_END_DAYS} days. Hiding (active=false) is independent.
 */
public final class MovieLifecycle {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final int LISTED_AFTER_END_DAYS = 7;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private MovieLifecycle() {}
    public static LocalDate today(Clock clock) { return clock.instant().atZone(ZONE).toLocalDate(); }
    public static LocalDate day(Instant instant) { return instant.atZone(ZONE).toLocalDate(); }
    public static String status(Movie movie, LocalDate today) {
        if (movie.releaseDate == null || movie.releaseDate.isAfter(today)) return "COMING_SOON";
        return movie.endDate != null && movie.endDate.isBefore(today) ? "ENDED" : "NOW_SHOWING";
    }
    public static String label(String status) {
        return switch (status) { case "COMING_SOON" -> "Sắp chiếu"; case "NOW_SHOWING" -> "Đang chiếu"; case "ENDED" -> "Đã kết thúc"; default -> "—"; };
    }
    /** Same rule in SQL/HQL, before filtering, counting and pagination. */
    public static String expression(String alias, String dateParameter) {
        return "(case when " + alias + ".releaseDate is null or " + alias + ".releaseDate>" + dateParameter + " then 'COMING_SOON' when " + alias + ".endDate<" + dateParameter + " then 'ENDED' else 'NOW_SHOWING' end)";
    }
    /** Shown on public lists: visible and not more than seven days past its end date. */
    public static boolean listed(Movie movie, LocalDate today) {
        return movie.active && (movie.endDate == null || !today.isAfter(movie.endDate.plusDays(LISTED_AFTER_END_DAYS)));
    }
    /** Earliest end date that is still listed publicly on {@code today}; use as {@code m.endDate >= listedFrom}. */
    public static LocalDate listedFrom(LocalDate today) { return today.minusDays(LISTED_AFTER_END_DAYS); }
    /** A showtime may only start on a Vietnam date inside the movie's release window. */
    public static boolean inWindow(Movie movie, Instant start) {
        LocalDate day = day(start);
        return (movie.releaseDate == null || !day.isBefore(movie.releaseDate)) && (movie.endDate == null || !day.isAfter(movie.endDate));
    }
    /** New bookings stop for showtimes after the end date (advance sales before release are allowed). */
    public static boolean afterEnd(Movie movie, Instant start) { return movie.endDate != null && day(start).isAfter(movie.endDate); }
    public static String windowText(Movie movie) {
        return (movie.releaseDate == null ? "?" : DAY.format(movie.releaseDate)) + " – " + (movie.endDate == null ? "?" : DAY.format(movie.endDate));
    }
    public static String windowError(Movie movie, Instant start) {
        return inWindow(movie, start) ? null : "Ngày chiếu phải nằm trong thời gian phát hành của phim (" + windowText(movie) + ").";
    }
}
