package vn.cineprime.staff;

import java.time.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.cineprime.auth.SecurityConfig.SessionUser;
import vn.cineprime.domain.Store;
import vn.cineprime.web.Catalog;
import static vn.cineprime.domain.Domain.*;

@Controller
public class StaffController {
    private final Store db;private final Catalog catalog;private final CheckInService checkin;
    public StaffController(Store db,Catalog catalog,CheckInService checkin){this.db=db;this.catalog=catalog;this.checkin=checkin;}
    @GetMapping("/staff") public String staff(@AuthenticationPrincipal SessionUser u,@RequestParam(required=false)LocalDate date,@RequestParam(required=false)Long showtimeId,Model model){User staff=db.get(User.class,u.id());LocalDate day=date==null?LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")):date;model.addAttribute("day",day);model.addAttribute("shows",db.list(Showtime.class,"where e.status='OPEN' order by e.startsAt").stream().filter(s->db.get(Room.class,s.roomId).cinemaId.equals(staff.cinemaId)&&s.startsAt.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().equals(day)).map(catalog::show).toList());model.addAttribute("selectedShow",showtimeId);
        if(showtimeId!=null){Showtime s=db.get(Showtime.class,showtimeId);checkin.permitted(u.id(),s);List<Ticket> tickets=db.list(Ticket.class,"where e.showtimeId=?1 order by e.id",showtimeId);model.addAttribute("tickets",tickets.stream().map(catalog::ticket).toList());model.addAttribute("checkedIn",tickets.stream().filter(t->t.status.equals("USED")).count());model.addAttribute("notCheckedIn",tickets.stream().filter(t->t.status.equals("VALID")).count());}return "staff";
    }
    @PostMapping("/api/staff/lookup") @ResponseBody public List<Map<String,Object>> lookup(@AuthenticationPrincipal SessionUser u,@RequestBody Map<String,String> body){return checkin.lookup(u.id(),body.getOrDefault("code","")).stream().map(catalog::ticket).toList();}
    public record CheckIn(Long ticketId,Long showtimeId){}
    @PostMapping("/api/staff/check-in") @ResponseBody public Map<String,Object> checkin(@AuthenticationPrincipal SessionUser u,@RequestBody CheckIn request){return catalog.ticket(checkin.checkIn(u.id(),request.ticketId(),request.showtimeId()));}
}
