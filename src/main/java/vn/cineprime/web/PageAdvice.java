package vn.cineprime.web;

import jakarta.servlet.http.*;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import vn.cineprime.auth.SecurityConfig.SessionUser;
import vn.cineprime.domain.Store;
import static vn.cineprime.domain.Domain.*;

@ControllerAdvice
public class PageAdvice {
    private final Store db;private final Clock clock;public PageAdvice(Store db,Clock clock){this.db=db;this.clock=clock;}
    @ModelAttribute public void common(Model model,Authentication authentication,HttpServletRequest request){
        // JSON responses do not render the shared page chrome.
        String path=request.getServletPath();
        if(path.startsWith("/api/")||path.equals("/catalog/showtimes")||path.equals("/payments/momo/ipn"))return;
        model.addAttribute("title","CinePrime · Hơn cả một buổi xem phim");model.addAttribute("currentPath",request.getServletPath());
        model.addAttribute("siteNotices",db.views(db.list(Content.class,"where e.kind='NOTICE' and e.active=true order by e.id desc").stream().limit(1).toList()));
        if(authentication!=null&&authentication.getPrincipal() instanceof SessionUser p){User u=db.get(User.class,p.id());model.addAttribute("viewer",db.view(u));}
        CsrfToken token=(CsrfToken)request.getAttribute(CsrfToken.class.getName());if(token!=null){model.addAttribute("csrfName",token.getParameterName());model.addAttribute("csrfToken",token.getToken());model.addAttribute("csrfHeader",token.getHeaderName());}
        model.addAttribute("cinemaOptions",db.views(db.list(Cinema.class,"where e.active=true order by e.city,e.name")));
        String requestedDay=request.getParameter("date");LocalDate today=LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")));
        model.addAttribute("days",java.util.stream.IntStream.range(0,7).mapToObj(i->{LocalDate day=today.plusDays(i);var link=org.springframework.web.util.UriComponentsBuilder.fromPath(request.getServletPath().equals("/")?"/showtimes":request.getServletPath());for(String key:List.of("movieId","cinemaId")){String value=request.getParameter(key);if(value!=null)link.queryParam(key,value);}link.queryParam("date",day);return Map.of("value",day.toString(),"href",link.build().encode().toUriString(),"selected",day.toString().equals(requestedDay==null?today.toString():requestedDay),"label",i==0?"Hôm nay":day.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")));}).toList());
    }
    @ExceptionHandler({IllegalArgumentException.class,DataAccessException.class,AccessDeniedException.class})
    public Object failure(Exception exception,HttpServletRequest request,HttpServletResponse response){
        int code=exception instanceof AccessDeniedException?403:exception instanceof DataAccessException?409:400;
        String message=exception instanceof DataAccessException?"Dữ liệu bị trùng, đang được cập nhật hoặc còn liên kết. Hãy tải lại và kiểm tra thông tin.":exception.getMessage();
        if(request.getServletPath().startsWith("/api/")||request.getServletPath().equals("/payments/momo/ipn"))return ResponseEntity.status(code).body(Map.of("message",message));
        response.setStatus(code);ModelAndView view=new ModelAndView("error");view.addObject("message",message);view.addObject("title","Không thể hoàn tất thao tác");return view;
    }
    @ExceptionHandler(org.springframework.web.client.RestClientException.class)
    public ModelAndView paymentUnavailable(HttpServletResponse response){response.setStatus(503);ModelAndView view=new ModelAndView("error");view.addObject("message","Chưa nhận được phản hồi MoMo. Nếu đã thanh toán, hãy mở đơn và Kiểm tra thanh toán; không cần đặt lại ghế.");return view;}
}
