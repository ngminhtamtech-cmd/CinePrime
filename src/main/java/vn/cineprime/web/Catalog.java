package vn.cineprime.web;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cineprime.domain.Store;
import static vn.cineprime.domain.Domain.*;

@Service @Transactional(readOnly=true)
public class Catalog {
    private final Store db;private final Clock clock;
    public Catalog(Store db,Clock clock){this.db=db;this.clock=clock;}
    public Map<String,Object> movie(Movie m){
        Map<String,Object> v=db.view(m);List<Review> reviews=db.list(Review.class,"where e.movieId=?1 and e.visible=true",m.id);
        v.put("score",reviews.isEmpty()?"Mới":String.format(Locale.ROOT,"%.1f",reviews.stream().mapToInt(r->r.stars).average().orElse(0)));
        v.put("reviewCount",reviews.size());v.put("genres",String.join(" · ",db.list(MovieGenre.class,"where e.movieId=?1",m.id).stream().map(g->db.get(Genre.class,g.genreId).name).toList()));
        if(m.poster==null||m.poster.isBlank())v.put("poster","/images/poster-1.svg");
        v.put("statusLabel",m.status.equals("COMING_SOON")?"Sắp chiếu":m.status.equals("NOW_SHOWING")?"Đang chiếu":"Ngừng chiếu");return v;
    }
    public List<Map<String,Object>> movies(String q,String status,Long genre,String age,LocalDate date){
        return db.list(Movie.class,"where e.active=true order by e.releaseDate desc,e.id desc").stream()
            .filter(m->q==null||m.title.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT)))
            .filter(m->status==null||status.isBlank()||m.status.equals(status))
            .filter(m->age==null||age.isBlank()||m.ageRating.equals(age))
            .filter(m->genre==null||!db.list(MovieGenre.class,"where e.movieId=?1 and e.genreId=?2",m.id,genre).isEmpty())
            .filter(m->date==null||!shows(m.id,null,date).isEmpty()).map(this::movie).toList();
    }
    public Map<String,Object> show(Showtime s){
        Map<String,Object> v=db.view(s);Movie m=db.get(Movie.class,s.movieId);Room r=db.get(Room.class,s.roomId);Cinema c=db.get(Cinema.class,r.cinemaId);
        v.put("movieTitle",m.title);v.put("movieSlug",m.slug);v.put("poster",m.poster);v.put("cinemaName",c.name);v.put("city",c.city);v.put("roomName",r.name);v.put("when",db.date(s.startsAt));v.put("cinemaId",c.id);v.put("canHold",!clock.instant().plusSeconds(1200).isAfter(s.startsAt));return v;
    }
    public List<Map<String,Object>> shows(Long movieId,Long cinemaId,LocalDate date){
        StringBuilder hql=new StringBuilder("select s,m,r,c from %s s, %s m, %s r, %s c where s.movieId=m.id and s.roomId=r.id and r.cinemaId=c.id and s.status='OPEN' and s.startsAt>?1 and m.active=true and m.status<>'STOPPED' and r.active=true and c.active=true".formatted(db.entityName(Showtime.class),db.entityName(Movie.class),db.entityName(Room.class),db.entityName(Cinema.class)));
        List<Object> args=new ArrayList<>();args.add(clock.instant().plusSeconds(900));
        if(movieId!=null){args.add(movieId);hql.append(" and s.movieId=?").append(args.size());}
        if(cinemaId!=null){args.add(cinemaId);hql.append(" and c.id=?").append(args.size());}
        if(date!=null){
            ZoneId zone=ZoneId.of("Asia/Ho_Chi_Minh");
            args.add(date.atStartOfDay(zone).toInstant());hql.append(" and s.startsAt>=?").append(args.size());
            args.add(date.plusDays(1).atStartOfDay(zone).toInstant());hql.append(" and s.startsAt<?").append(args.size());
        }
        // Load related records together into this transaction, rather than querying per show.
        return db.query(hql.append(" order by s.startsAt").toString(),Object[].class,args.toArray()).stream().map(row->show((Showtime)row[0])).toList();
    }
    public Map<String,Object> order(Order o){Map<String,Object> v=db.view(o);v.put("when",db.date(o.showStartsAt));v.put("totalText",db.money(o.total));v.put("subtotalText",db.money(o.subtotal));v.put("discountText",db.money(o.discount));v.put("deadline",o.expiresAt.toString());v.put("expired",!clock.instant().isBefore(o.expiresAt));return v;}
    public Map<String,Object> ticket(Ticket t){Map<String,Object> v=db.view(t);Order o=db.get(Order.class,t.orderId);v.put("order",order(o));v.put("seat",db.get(Seat.class,t.seatId).label);v.put("when",db.date(t.checkedInAt));return v;}
}
