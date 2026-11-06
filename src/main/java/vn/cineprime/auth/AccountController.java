package vn.cineprime.auth;

import java.time.*;
import java.util.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.cineprime.auth.SecurityConfig.SessionUser;
import vn.cineprime.auth.SecurityConfig.RateLimit;
import vn.cineprime.domain.Store;
import vn.cineprime.media.MediaService;
import vn.cineprime.web.Catalog;
import static vn.cineprime.domain.Domain.*;

@Controller
public class AccountController {
    private final AccountService accounts;private final Store db;private final Catalog catalog;private final RateLimit rates;private final MediaService media;private final Clock clock;
    public AccountController(AccountService accounts,Store db,Catalog catalog,RateLimit rates,MediaService media,Clock clock){this.accounts=accounts;this.db=db;this.catalog=catalog;this.rates=rates;this.media=media;this.clock=clock;}
    @GetMapping({"/login","/register","/forgot-password","/reset-password"}) public String auth(HttpServletRequest request,@RequestParam(defaultValue="")String token,Model model){String mode=request.getServletPath().substring(1);model.addAttribute("mode",mode);model.addAttribute("resetToken",token);model.addAttribute("title",switch(mode){case "register"->"Tạo tài khoản CinePrime";case "forgot-password"->"Quên mật khẩu";case "reset-password"->"Đặt lại mật khẩu";default->"Chào mừng trở lại";});return "auth";}
    private void limited(String key,String ip){if(!rates.allow(key+ip,5,300))throw new IllegalArgumentException("Bạn đã thử quá nhiều lần. Vui lòng thử lại sau 5 phút.");}
    @PostMapping("/register") public String register(@RequestParam String email,@RequestParam String name,@RequestParam String password,HttpServletRequest req){limited("register:",req.getRemoteAddr());accounts.register(email,name,password);return "redirect:/login?registered";}
    @PostMapping("/forgot-password") public String forgot(@RequestParam String email,HttpServletRequest req){limited("reset:",req.getRemoteAddr());accounts.forgot(email);return "redirect:/forgot-password?sent";}
    @PostMapping("/reset-password") public String reset(@RequestParam String token,@RequestParam String password,HttpServletRequest req){limited("reset-use:",req.getRemoteAddr());accounts.reset(token,password);return "redirect:/login?reset";}
    @GetMapping("/account") public String account(@AuthenticationPrincipal SessionUser u,Model model){model.addAttribute("orders",db.list(Order.class,"where e.userId=?1 order by e.createdAt desc",u.id()).stream().map(catalog::order).toList());model.addAttribute("points",db.list(Point.class,"where e.userId=?1 order by e.createdAt desc",u.id()).stream().map(p->{Map<String,Object> v=db.view(p);v.put("when",db.date(p.createdAt));return v;}).toList());model.addAttribute("balance",db.list(Point.class,"where e.userId=?1",u.id()).stream().mapToLong(p->p.points).sum());model.addAttribute("favorites",db.list(Favorite.class,"where e.userId=?1",u.id()).stream().map(f->catalog.movie(db.get(Movie.class,f.movieId))).toList());model.addAttribute("subscriptions",db.list(Subscription.class,"where e.userId=?1",u.id()).stream().map(s->{Map<String,Object> v=db.view(s);v.put("title",db.get(Movie.class,s.movieId).title);return v;}).toList());model.addAttribute("notices",db.list(Notification.class,"where e.userId=?1 order by e.createdAt desc",u.id()).stream().map(n->{Map<String,Object> v=db.view(n);v.put("when",db.date(n.createdAt));return v;}).toList());return "account";}
    @PostMapping("/account/profile") public String profile(@AuthenticationPrincipal SessionUser u,@RequestParam String name,@RequestParam(defaultValue="")String phone,@RequestParam(required=false)MultipartFile avatar)throws Exception{accounts.profile(u.id(),name,phone,avatar!=null&&!avatar.isEmpty()?media.upload(avatar,false):null);return "redirect:/account?saved";}
    @PostMapping("/account/password") public String password(@AuthenticationPrincipal SessionUser u,@RequestParam String oldPassword,@RequestParam String password){accounts.changePassword(u.id(),oldPassword,password);return "redirect:/login?reset";}
    @PostMapping("/account/favorites/{movieId}") @Transactional public String favorite(@AuthenticationPrincipal SessionUser u,@PathVariable Long movieId){db.lock(User.class,u.id());Movie m=db.get(Movie.class,movieId);Optional<Favorite> f=db.first(Favorite.class,"where e.userId=?1 and e.movieId=?2",u.id(),movieId);if(f.isPresent())db.delete(f.get());else{Favorite n=new Favorite();n.userId=u.id();n.movieId=movieId;db.save(n);}return "redirect:/movies/"+m.slug;}
    @PostMapping("/account/subscriptions/{movieId}") @Transactional public String subscribe(@AuthenticationPrincipal SessionUser u,@PathVariable Long movieId,@RequestParam(defaultValue="false")boolean email){db.lock(User.class,u.id());Movie m=db.get(Movie.class,movieId);Optional<Subscription> old=db.first(Subscription.class,"where e.userId=?1 and e.movieId=?2",u.id(),movieId);if(old.isPresent())db.delete(old.get());else{Subscription s=new Subscription();s.userId=u.id();s.movieId=movieId;s.email=email;db.save(s);}return "redirect:/movies/"+m.slug;}
    @PostMapping("/account/reviews/{movieId}") @Transactional public String review(@AuthenticationPrincipal SessionUser u,@PathVariable Long movieId,@RequestParam int stars,@RequestParam String comment){
        db.lock(User.class,u.id());Movie m=db.get(Movie.class,movieId);
        boolean eligible=db.list(Order.class,"where e.userId=?1 and e.status='PAID'",u.id()).stream().anyMatch(o->{Showtime s=db.get(Showtime.class,o.showtimeId);return s.movieId.equals(movieId)&&!clock.instant().isBefore(s.endsAt)&&!db.list(Ticket.class,"where e.orderId=?1 and e.status='USED'",o.id).isEmpty();});
        if(!eligible)throw new IllegalArgumentException("Chỉ đánh giá sau khi bạn đã check-in và suất chiếu kết thúc.");if(stars<1||stars>5||comment.isBlank()||comment.length()>2000)throw new IllegalArgumentException("Chọn 1–5 sao và nhận xét tối đa 2.000 ký tự.");
        Review r=db.first(Review.class,"where e.userId=?1 and e.movieId=?2",u.id(),movieId).orElseGet(Review::new);r.userId=u.id();r.movieId=movieId;r.stars=stars;r.comment=comment;r.createdAt=clock.instant();db.save(r);return "redirect:/movies/"+m.slug+"#reviews";
    }
    @PostMapping("/account/notifications/{id}/read") @Transactional public String read(@AuthenticationPrincipal SessionUser u,@PathVariable Long id){Notification n=db.get(Notification.class,id);if(!n.userId.equals(u.id()))throw new org.springframework.security.access.AccessDeniedException("Không được truy cập.");n.seen=true;return "redirect:/account#notifications";}
}
