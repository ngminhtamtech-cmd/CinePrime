package vn.cineprime;

import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import vn.cineprime.domain.Domain.Movie;
import vn.cineprime.domain.MovieLifecycle;

class MovieLifecycleTest {
    static Movie movie(LocalDate release,LocalDate end){Movie m=new Movie();m.releaseDate=release;m.endDate=end;return m;}
    @Test void switchesAtVietnamMidnightWithoutAJobAndFollowsDateEdits() {
        Movie movie=movie(LocalDate.of(2026,11,9),LocalDate.of(2026,12,9));
        Clock before=Clock.fixed(Instant.parse("2026-11-08T16:59:59Z"),ZoneOffset.ofHours(-8));
        Clock after=Clock.offset(before,Duration.ofSeconds(1));
        assertEquals("COMING_SOON",MovieLifecycle.status(movie,MovieLifecycle.today(before)));
        assertEquals("NOW_SHOWING",MovieLifecycle.status(movie,MovieLifecycle.today(after)));
        assertEquals("NOW_SHOWING",MovieLifecycle.status(movie,LocalDate.of(2026,12,9)),"End date is the last showing day");
        assertEquals("ENDED",MovieLifecycle.status(movie,LocalDate.of(2026,12,10)));
        movie.releaseDate=LocalDate.of(2026,12,1);movie.status="NOW_SHOWING";
        assertEquals("COMING_SOON",MovieLifecycle.status(movie,MovieLifecycle.today(after)),"Stored status never overrides dates");
    }
    @Test void endedMoviesStayListedForSevenDaysAndHiddenIsIndependent() {
        Movie movie=movie(LocalDate.of(2026,11,1),LocalDate.of(2026,11,30));
        assertTrue(MovieLifecycle.listed(movie,LocalDate.of(2026,12,7)));
        assertFalse(MovieLifecycle.listed(movie,LocalDate.of(2026,12,8)));
        assertEquals(LocalDate.of(2026,11,30),MovieLifecycle.listedFrom(LocalDate.of(2026,12,7)));
        movie.active=false;assertFalse(MovieLifecycle.listed(movie,LocalDate.of(2026,11,15)));
        assertEquals("NOW_SHOWING",MovieLifecycle.status(movie,LocalDate.of(2026,11,15)));
    }
    @Test void showtimeWindowUsesVietnamDatesInclusive() {
        Movie movie=movie(LocalDate.of(2026,12,1),LocalDate.of(2026,12,31));
        assertFalse(MovieLifecycle.inWindow(movie,Instant.parse("2026-11-30T16:59:00Z")),"23:59 on 30/11 in Vietnam");
        assertTrue(MovieLifecycle.inWindow(movie,Instant.parse("2026-11-30T17:00:00Z")),"00:00 on 01/12 in Vietnam");
        assertTrue(MovieLifecycle.inWindow(movie,Instant.parse("2026-12-31T16:59:00Z")));
        assertFalse(MovieLifecycle.inWindow(movie,Instant.parse("2026-12-31T17:00:00Z")));
        assertTrue(MovieLifecycle.afterEnd(movie,Instant.parse("2026-12-31T17:00:00Z")));
        assertFalse(MovieLifecycle.afterEnd(movie,Instant.parse("2026-11-01T03:00:00Z")),"Advance sales before release are not blocked at booking time");
        assertTrue(MovieLifecycle.windowError(movie,Instant.parse("2026-11-01T03:00:00Z")).contains("01/12/2026 – 31/12/2026"));
    }
}
