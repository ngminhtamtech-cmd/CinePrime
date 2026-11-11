package vn.cineprime;

import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import vn.cineprime.domain.Domain.Movie;
import vn.cineprime.domain.MovieLifecycle;

class MovieLifecycleTest {
    @Test void switchesAtVietnamMidnightWithoutAJobAndFollowsDateEdits() {
        Movie movie=new Movie();movie.releaseDate=LocalDate.of(2026,11,9);
        Clock before=Clock.fixed(Instant.parse("2026-11-08T16:59:59Z"),ZoneOffset.ofHours(-8));
        Clock after=Clock.offset(before,Duration.ofSeconds(1));
        assertEquals("COMING_SOON",MovieLifecycle.status(movie,MovieLifecycle.today(before)));
        assertEquals("NOW_SHOWING",MovieLifecycle.status(movie,MovieLifecycle.today(after)));
        assertEquals("NOW_SHOWING",MovieLifecycle.status(movie,LocalDate.of(2026,11,20)));
        movie.releaseDate=LocalDate.of(2026,12,1);movie.status="NOW_SHOWING";
        assertEquals("COMING_SOON",MovieLifecycle.status(movie,MovieLifecycle.today(after)));
        movie.releaseDate=LocalDate.of(2026,11,1);
        assertEquals("NOW_SHOWING",MovieLifecycle.status(movie,MovieLifecycle.today(after)));
    }
    @Test void stoppedAndLegacyUndatedStatusRemainExplicitAndVisibilityIsIndependent() {
        Movie movie=new Movie();movie.status="STOPPED";movie.releaseDate=LocalDate.of(2020,1,1);
        assertEquals("STOPPED",MovieLifecycle.status(movie,LocalDate.of(2026,11,9)));
        movie.releaseDate=null;movie.status="NOW_SHOWING";
        assertEquals("NOW_SHOWING",MovieLifecycle.status(movie,LocalDate.of(2026,11,9)));
        movie.status="COMING_SOON";assertEquals("COMING_SOON",MovieLifecycle.status(movie,LocalDate.of(2026,11,9)));
        movie.active=false;movie.releaseDate=LocalDate.of(2026,11,8);
        assertEquals("NOW_SHOWING",MovieLifecycle.status(movie,LocalDate.of(2026,11,9)));
        assertFalse(movie.active);
    }
}
