package vn.cineprime.web;

import java.time.*;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.cineprime.auth.SecurityConfig.SessionUser;
import vn.cineprime.domain.Store;
import static vn.cineprime.domain.Domain.*;

@Controller
public class Pages {
    private final Store db;private final Catalog catalog;private final Clock clock;
    public Pages(Store db,Catalog catalog,Clock clock){this.db=db;this.catalog=catalog;this.clock=clock;}
    @GetMapping("/") public String home(Model model){model.addAttribute("movies",catalog.movies(null,"NOW_SHOWING",null,null,null));model.addAttribute("coming",catalog.movies(null,"COMING_SOON",null,null,null));model.addAttribute("featured",catalog.movies(null,null,null,null,null).stream().filter(m->Boolean.TRUE.equals(m.get("featured"))).sorted((a,b)->Long.compare(((Number)b.get("id")).longValue(),((Number)a.get("id")).longValue())).toList());model.addAttribute("shows",catalog.shows(null,null,null).stream().limit(8).toList());model.addAttribute("offers",db.views(db.list(Content.class,"where e.kind='OFFER' and e.active=true order by e.id desc").stream().limit(3).toList()));model.addAttribute("banners",db.views(db.list(Content.class,"where e.kind='BANNER' and e.active=true order by e.id desc")));model.addAttribute("articles",db.views(db.list(Content.class,"where e.kind='ARTICLE' and e.active=true order by e.id desc").stream().limit(3).toList()));return "home";}
    @GetMapping("/movies") public String movies(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="")String status,@RequestParam(required=false)Long genre,@RequestParam(defaultValue="")String age,@RequestParam(required=false)LocalDate date,Model model){model.addAttribute("movies",catalog.movies(q,status,genre,age,date));model.addAttribute("genres",db.views(db.list(Genre.class,"where e.active=true")));model.addAttribute("q",q);model.addAttribute("selectedStatus",status);model.addAttribute("selectedGenre",genre);model.addAttribute("selectedAge",age);model.addAttribute("selectedDate",date);return "movies";}
    @GetMapping("/movies/{slug}") public String detail(@PathVariable String slug,@RequestParam(required=false)Long cinemaId,@RequestParam(required=false)LocalDate date,@AuthenticationPrincipal SessionUser user,Model model){
        Movie movie=db.first(Movie.class,"where e.slug=?1 and e.active=true and e.status<>'STOPPED'",slug).orElseThrow(()->new IllegalArgumentException("Không tìm thấy phim."));model.addAttribute("movie",catalog.movie(movie));model.addAttribute("shows",catalog.shows(movie.id,cinemaId,date));
        List<Credit> links=db.list(Credit.class,"where e.movieId=?1 order by e.id",movie.id);Map<Long,String> names=new HashMap<>();Set<Long> personIds=new HashSet<>();links.forEach(c->personIds.add(c.personId));if(!personIds.isEmpty())db.entityManager().createQuery("from "+db.entityName(Person.class)+" p where p.id in :ids",Person.class).setParameter("ids",personIds).getResultList().forEach(p->names.put(p.id,p.name));
        List<Map<String,Object>> credits=links.stream().map(c->{Map<String,Object> v=db.view(c);v.put("name",names.getOrDefault(c.personId,"—"));return v;}).toList();model.addAttribute("credits",credits);
        model.addAttribute("reviews",db.list(Review.class,"where e.movieId=?1 and e.visible=true order by e.createdAt desc",movie.id).stream().map(r->{Map<String,Object> v=db.view(r);v.put("name",db.get(User.class,r.userId).name);return v;}).toList());
        model.addAttribute("favorite",user!=null&&db.first(Favorite.class,"where e.userId=?1 and e.movieId=?2",user.id(),movie.id).isPresent());model.addAttribute("subscribed",user!=null&&db.first(Subscription.class,"where e.userId=?1 and e.movieId=?2",user.id(),movie.id).isPresent());return "movie";
    }
    @GetMapping("/showtimes") public String showtimes(@RequestParam(required=false)Long movieId,@RequestParam(required=false)Long cinemaId,@RequestParam(required=false)LocalDate date,Model model){LocalDate day=date==null?LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh"))):date;model.addAttribute("shows",catalog.shows(movieId,cinemaId,day));model.addAttribute("movieOptions",catalog.movies(null,null,null,null,null));model.addAttribute("selectedMovie",movieId);model.addAttribute("selectedCinema",cinemaId);model.addAttribute("selectedDate",day);return "showtimes";}
    @GetMapping("/catalog/showtimes") @ResponseBody public List<Map<String,Object>> quickOptions(){return catalog.shows(null,null,null).stream().map(s->{Map<String,Object> v=new HashMap<>();v.put("id",s.get("id"));v.put("movieId",s.get("movieId"));v.put("cinemaId",s.get("cinemaId"));v.put("cinemaName",s.get("cinemaName"));v.put("when",s.get("when"));v.put("date",((Instant)s.get("startsAt")).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().toString());v.put("canHold",s.get("canHold"));return v;}).toList();}
    @GetMapping("/cinemas") public String cinemas(Model model){model.addAttribute("cinemas",db.views(db.list(Cinema.class,"where e.active=true order by e.city,e.name")));return "cinemas";}
    @GetMapping("/cinemas/{id}") public String cinema(@PathVariable Long id,@RequestParam(required=false)LocalDate date,Model model){Cinema c=db.get(Cinema.class,id);if(!c.active)throw new IllegalArgumentException("Rạp đã ngừng hoạt động.");model.addAttribute("cinema",db.view(c));model.addAttribute("shows",catalog.shows(null,id,date));model.addAttribute("prices",db.list(Price.class,"where e.cinemaId=?1 order by e.dayType,e.seatType,e.fromMinute",id).stream().map(p->{Map<String,Object> v=db.view(p);v.put("amountText",db.money(p.amount));v.put("time",String.format("%02d:%02d – %02d:%02d",p.fromMinute/60,p.fromMinute%60,p.toMinute/60,p.toMinute%60));return v;}).toList());return "cinema";}
    @GetMapping({"/offers","/articles"}) public String contents(HttpServletRequest request,Model model){boolean offers=request.getServletPath().equals("/offers");model.addAttribute("title",offers?"Ưu đãi dành cho bạn":"Góc điện ảnh");model.addAttribute("contents",db.views(db.list(Content.class,"where e.kind=?1 and e.active=true order by e.publishedAt desc",offers?"OFFER":"ARTICLE")));return "contents";}
    @GetMapping("/content/{slug}") public String content(@PathVariable String slug,Model model){model.addAttribute("content",db.view(db.first(Content.class,"where e.slug=?1 and e.active=true",slug).orElseThrow(()->new IllegalArgumentException("Chưa có nội dung này."))));return "content";}
}
