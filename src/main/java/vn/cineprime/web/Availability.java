package vn.cineprime.web;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cineprime.domain.MovieLifecycle;
import vn.cineprime.domain.Store;
import static vn.cineprime.domain.Domain.*;

/** Public discovery/new bookings only; existing orders and tickets retain their own eligibility. */
@Service @Transactional(readOnly=true)
public class Availability {
    private final Store db;private final Clock clock;
    public Availability(Store db,Clock clock){this.db=db;this.clock=clock;}
    public Showtime bookable(Long id){Showtime s=db.get(Showtime.class,id);Room r=db.get(Room.class,s.roomId);Cinema c=db.get(Cinema.class,r.cinemaId);Movie m=db.get(Movie.class,s.movieId);if(!s.status.equals("OPEN")||!m.active||MovieLifecycle.afterEnd(m,s.startsAt)||!r.active||!c.active||clock.instant().plusSeconds(1200).isAfter(s.startsAt))throw new IllegalArgumentException("Suất không còn nhận đặt vé mới. Vui lòng chọn suất khác.");return s;}
}
