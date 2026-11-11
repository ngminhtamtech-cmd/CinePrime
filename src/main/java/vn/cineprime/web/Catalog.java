package vn.cineprime.web;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cineprime.domain.Store;
import vn.cineprime.domain.MovieLifecycle;
import static vn.cineprime.domain.Domain.*;

@Service @Transactional(readOnly=true)
public class Catalog {
    private final Store db;private final Clock clock;private final vn.cineprime.media.MediaService media;
    public Catalog(Store db,Clock clock,vn.cineprime.media.MediaService media){this.db=db;this.clock=clock;this.media=media;}
    public Map<String,Object> movie(Movie m){return movieViews(List.of(m)).getFirst();}
    private Map<String,Object> movieView(Movie m,String genres,long count,Double score){
        Map<String,Object> v=db.view(m);
        v.put("score",count==0?"Mới":String.format(Locale.ROOT,"%.1f",score));
        v.put("reviewCount",count);v.put("genres",genres.isBlank()?"Chưa phân loại":genres);
        v.put("poster",media.imageOrPlaceholder(m.poster));v.put("banner",media.bannerOrEmpty(m.banner));try{v.put("trailer",media.trailer(m.trailer));}catch(IllegalArgumentException e){v.put("trailer","");}
        v.put("statusLabel",MovieLifecycle.label(v.get("status").toString()));v.put("ended","ENDED".equals(v.get("status")));v.put("comingSoon","COMING_SOON".equals(v.get("status")));v.put("windowText",MovieLifecycle.windowText(m));v.put("releaseText",m.releaseDate==null?"":m.releaseDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));return v;
    }
    public List<Map<String,Object>> movies(String q,String status,Long genre,String age,LocalDate date){
        StringBuilder hql=new StringBuilder("select m from "+db.entityName(Movie.class)+" m where m.active=true and (m.endDate is null or m.endDate>=:listedFrom)");Map<String,Object> args=new HashMap<>();args.put("listedFrom",MovieLifecycle.listedFrom(MovieLifecycle.today(clock)));
        if(q!=null&&!q.isBlank()){hql.append(" and lower(m.title) like :q escape '!'");args.put("q","%"+q.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%");}
        if(status!=null&&!status.isBlank()){hql.append(" and ").append(MovieLifecycle.expression("m",":movieToday")).append("=:status");args.put("status",status);args.put("movieToday",MovieLifecycle.today(clock));}
        if(age!=null&&!age.isBlank()){hql.append(" and m.ageRating=:age");args.put("age",age);}
        if(genre!=null){hql.append(" and exists (select x.id from "+db.entityName(MovieGenre.class)+" x, "+db.entityName(Genre.class)+" g where x.movieId=m.id and x.genreId=g.id and g.active=true and g.id=:genre)");args.put("genre",genre);}
        if(date!=null){hql.append(" and exists (select s.id from "+db.entityName(Showtime.class)+" s, "+db.entityName(Room.class)+" r, "+db.entityName(Cinema.class)+" c where s.movieId=m.id and s.roomId=r.id and r.cinemaId=c.id and s.status='OPEN' and r.active=true and c.active=true and s.startsAt>:cutoff and s.startsAt>=:from and s.startsAt<:until)");args.put("cutoff",clock.instant().plusSeconds(900));args.put("from",date.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant());args.put("until",date.plusDays(1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant());}
        var query=db.entityManager().createQuery(hql+" order by m.releaseDate desc,m.id desc",Movie.class);args.forEach(query::setParameter);return movieViews(query.getResultList());
    }
    private List<Map<String,Object>> movieViews(List<Movie> movies){
        if(movies.isEmpty())return List.of();var ids=movies.stream().map(m->m.id).toList();Map<Long,List<String>> genres=new HashMap<>();Map<Long,Object[]> reviews=new HashMap<>();
        db.query("select x.movieId,g.name from "+db.entityName(MovieGenre.class)+" x, "+db.entityName(Genre.class)+" g where x.genreId=g.id and g.active=true and x.movieId in ?1 order by g.name,g.id",Object[].class,ids).forEach(r->genres.computeIfAbsent((Long)r[0],k->new ArrayList<>()).add((String)r[1]));
        db.query("select r.movieId,count(r),avg(r.stars) from "+db.entityName(Review.class)+" r where r.visible=true and r.movieId in ?1 group by r.movieId",Object[].class,ids).forEach(r->reviews.put((Long)r[0],r));
        Set<Long> selling=new HashSet<>(db.query("select distinct s.movieId from "+db.entityName(Showtime.class)+" s, "+db.entityName(Room.class)+" r, "+db.entityName(Cinema.class)+" c where s.roomId=r.id and r.cinemaId=c.id and s.movieId in ?1 and s.status='OPEN' and r.active=true and c.active=true and s.startsAt>=?2",Long.class,ids,clock.instant().plusSeconds(1200)));
        return movies.stream().map(m->{Object[] r=reviews.get(m.id);var v=movieView(m,String.join(" · ",genres.getOrDefault(m.id,List.of())),r==null?0:(Long)r[1],r==null?0:(Double)r[2]);v.put("hasBookableShows",m.active&&!"ENDED".equals(v.get("status"))&&selling.contains(m.id));return v;}).toList();
    }
    public Map<String,Object> show(Showtime s){
        Map<String,Object> v=db.view(s);Movie m=db.get(Movie.class,s.movieId);Room r=db.get(Room.class,s.roomId);Cinema c=db.get(Cinema.class,r.cinemaId);
        v.put("movieTitle",m.title);v.put("movieSlug",m.slug);v.put("poster",media.imageOrPlaceholder(m.poster));v.put("cinemaName",c.name);v.put("city",c.city);v.put("roomName",r.name);v.put("when",db.date(s.startsAt));v.put("cinemaId",c.id);v.put("canHold",!clock.instant().plusSeconds(1200).isAfter(s.startsAt));return v;
    }
    public List<Map<String,Object>> shows(Long movieId,Long cinemaId,LocalDate date){
        StringBuilder hql=new StringBuilder("select s,m,r,c from %s s, %s m, %s r, %s c where s.movieId=m.id and s.roomId=r.id and r.cinemaId=c.id and s.status='OPEN' and s.startsAt>?1 and m.active=true and r.active=true and c.active=true".formatted(db.entityName(Showtime.class),db.entityName(Movie.class),db.entityName(Room.class),db.entityName(Cinema.class)));
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
    public Map<String,Object> order(Order o){Map<String,Object> v=db.view(o);v.put("when",db.date(o.showStartsAt));v.put("totalText",db.money(o.total));v.put("subtotalText",db.money(o.subtotal));v.put("discountText",db.money(o.discount));v.put("deadline",o.expiresAt.toString());v.put("expired",!clock.instant().isBefore(o.expiresAt));v.put("statusLabel",switch(o.status){case "PENDING_PAYMENT"->"Chưa thanh toán";case "PAID"->o.total==0?"Miễn phí hợp lệ":"Đã thanh toán";case "CANCELLED"->"Đã hủy";case "EXPIRED"->"Hết hạn";default->o.status;});return v;}
    public Map<String,Object> ticket(Ticket t){Map<String,Object> v=db.view(t);Order o=db.get(Order.class,t.orderId);v.put("order",order(o));v.put("seat",db.get(Seat.class,t.seatId).label);v.put("when",db.date(t.checkedInAt));return v;}
}
