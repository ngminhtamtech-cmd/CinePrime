package vn.cineprime.staff;

import java.time.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.cineprime.auth.SecurityConfig.SessionUser;

@Controller
public class StaffController {
    private final StaffReadService reads;private final CheckInService checkin;
    public StaffController(StaffReadService reads,CheckInService checkin){this.reads=reads;this.checkin=checkin;}
    @GetMapping("/staff") public String staff(@AuthenticationPrincipal SessionUser user,@RequestParam(required=false)LocalDate date,@RequestParam(required=false)Long showtimeId,Model model){model.addAllAttributes(reads.page(user.id(),date,showtimeId));model.addAttribute("title","Soát vé tại rạp · CinePrime");return Boolean.TRUE.equals(model.getAttribute("unassigned"))?"staff-unassigned":"staff";}
    public record Lookup(String code,Long showtimeId){}
    @PostMapping("/api/staff/lookup") @ResponseBody public Map<String,Object> lookup(@AuthenticationPrincipal SessionUser user,@RequestBody Lookup request){return reads.lookup(user.id(),Objects.toString(request.code(),""),request.showtimeId());}
    public record CheckIn(Long ticketId,Long showtimeId){}
    @PostMapping("/api/staff/check-in") @ResponseBody public Map<String,Object> checkin(@AuthenticationPrincipal SessionUser user,@RequestBody CheckIn request){checkin.checkIn(user.id(),request.ticketId(),request.showtimeId());return Map.of("ticket",reads.one(user.id(),request.ticketId(),request.showtimeId()),"data",reads.tickets(user.id(),request.showtimeId(),"","",0));}
    @GetMapping("/api/staff/showtimes/{id}/tickets") @ResponseBody public Map<String,Object> tickets(@AuthenticationPrincipal SessionUser user,@PathVariable Long id,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="")String state,@RequestParam(defaultValue="0")int page){return reads.tickets(user.id(),id,q,state,page);}
    @GetMapping("/api/staff/tickets/{id}") @ResponseBody public Map<String,Object> ticket(@AuthenticationPrincipal SessionUser user,@PathVariable Long id,@RequestParam Long showtimeId){return reads.one(user.id(),id,showtimeId);}
}
