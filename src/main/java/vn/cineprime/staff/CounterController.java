package vn.cineprime.staff;

import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.cineprime.auth.SecurityConfig.SessionUser;
import vn.cineprime.domain.Store;
import vn.cineprime.media.TicketFiles;
import static vn.cineprime.domain.Domain.*;

/** Box-office sales for Staff. Pages are server-rendered; the sale flow uses small JSON endpoints under /api/staff/counter. */
@Controller
public class CounterController {
    private final CounterService counter;private final CounterReadService reads;private final CheckInService checkin;private final TicketFiles files;private final Store db;
    public CounterController(CounterService counter,CounterReadService reads,CheckInService checkin,TicketFiles files,Store db){this.counter=counter;this.reads=reads;this.checkin=checkin;this.files=files;this.db=db;}
    @GetMapping("/staff/counter") public String page(@AuthenticationPrincipal SessionUser user,@RequestParam(required=false)LocalDate date,Model model){model.addAllAttributes(reads.page(user.id(),date));model.addAttribute("title","Bán vé tại quầy · CinePrime");model.addAttribute("staffTab","counter");return Boolean.TRUE.equals(model.getAttribute("unassigned"))?"staff-unassigned":"counter";}
    @PostMapping("/staff/counter/shift/open") public String open(@AuthenticationPrincipal SessionUser user,@RequestParam(required=false)String openingCash,RedirectAttributes flash){
        try{counter.open(user.id(),amount(openingCash,"Tiền đầu ca"));flash.addFlashAttribute("notice","Đã mở ca. Có thể bắt đầu bán vé.");}catch(IllegalArgumentException|org.springframework.dao.DataAccessException e){flash.addFlashAttribute("actionError",e instanceof IllegalArgumentException?e.getMessage():"Ca vừa được mở ở nơi khác. Tải lại trang.");}return "redirect:/staff/counter";
    }
    @PostMapping("/staff/counter/shift/close") public String close(@AuthenticationPrincipal SessionUser user,@RequestParam(required=false)String countedCash,@RequestParam(required=false)String note,RedirectAttributes flash){
        try{Shift s=counter.close(user.id(),amount(countedCash,"Tiền đếm được"),note);flash.addFlashAttribute("notice","Đã đóng ca. In phiếu tổng kết nếu cần.");return "redirect:/staff/counter/shifts/"+s.id;}catch(IllegalArgumentException|org.springframework.dao.DataAccessException e){flash.addFlashAttribute("actionError",e instanceof IllegalArgumentException?e.getMessage():"Ca vừa thay đổi. Tải lại trang.");flash.addFlashAttribute("closeNote",Objects.toString(note,""));flash.addFlashAttribute("closeCounted",Objects.toString(countedCash,""));return "redirect:/staff/counter#close-shift";}
    }
    private static Long amount(String raw,String field){String value=raw==null?"":raw.replaceAll("[\\s.,₫đ]","");if(!value.matches("\\d{1,12}"))throw new IllegalArgumentException(field+" phải là số nguyên đồng.");return Long.valueOf(value);}
    @GetMapping("/staff/counter/shifts/{id}") public String shift(@AuthenticationPrincipal SessionUser user,@PathVariable Long id,Model model){Shift s=db.get(Shift.class,id);if(!s.staffId.equals(user.id()))throw new org.springframework.security.access.AccessDeniedException("Không xem được ca của nhân viên khác.");model.addAttribute("shift",reads.shift(s));model.addAttribute("title","Phiếu tổng kết ca · CinePrime");return "counter-shift";}

    @GetMapping("/api/staff/counter/showtimes/{id}/seats") @ResponseBody public Map<String,Object> seats(@AuthenticationPrincipal SessionUser user,@PathVariable Long id){return reads.seats(user.id(),id);}
    public record HoldRequest(Long showtimeId,List<Long> seats){}
    @PostMapping("/api/staff/counter/holds") @ResponseBody public Map<String,Object> hold(@AuthenticationPrincipal SessionUser user,@RequestBody HoldRequest request){Order o=counter.hold(user.id(),request.showtimeId(),request.seats());return reads.orderView(user.id(),o.id);}
    @GetMapping("/api/staff/counter/orders/{id}") @ResponseBody public Map<String,Object> order(@AuthenticationPrincipal SessionUser user,@PathVariable Long id){return reads.orderView(user.id(),id);}
    public record ComboRequest(Map<Long,Integer> quantities){}
    @PostMapping("/api/staff/counter/orders/{id}/combos") @ResponseBody public Map<String,Object> combos(@AuthenticationPrincipal SessionUser user,@PathVariable Long id,@RequestBody ComboRequest request){counter.combos(user.id(),id,request.quantities()==null?Map.of():request.quantities());return reads.orderView(user.id(),id);}
    @PostMapping("/api/staff/counter/orders/{id}/release") @ResponseBody public Map<String,Object> release(@AuthenticationPrincipal SessionUser user,@PathVariable Long id){counter.release(user.id(),id);return Map.of("released",true);}
    public record PayRequest(Long received,Long customerId,String requestKey){}
    @PostMapping("/api/staff/counter/orders/{id}/pay") @ResponseBody public Map<String,Object> pay(@AuthenticationPrincipal SessionUser user,@PathVariable Long id,@RequestBody PayRequest request){counter.pay(user.id(),id,request.received(),request.customerId(),request.requestKey());return reads.orderView(user.id(),id);}
    public record CancelRequest(String reason){}
    @PostMapping("/api/staff/counter/orders/{id}/cancel") @ResponseBody public Map<String,Object> cancel(@AuthenticationPrincipal SessionUser user,@PathVariable Long id,@RequestBody CancelRequest request){counter.cancel(user.id(),id,request.reason());return reads.orderView(user.id(),id);}
    /** Each ticket is checked in its own transaction with the normal check-in rules; one refusal does not undo the others. */
    @PostMapping("/api/staff/counter/orders/{id}/check-in") @ResponseBody public Map<String,Object> checkIn(@AuthenticationPrincipal SessionUser user,@PathVariable Long id){
        Order o=reads.owned(user.id(),id);if(!o.status.equals("PAID"))throw new IllegalArgumentException("Chỉ check-in đơn đã thu tiền.");List<Map<String,Object>> results=new ArrayList<>();
        for(Ticket t:db.list(Ticket.class,"where e.orderId=?1 order by e.id",id)){String seat=db.get(Seat.class,t.seatId).label;if(!t.status.equals("VALID")){results.add(Map.of("seat",seat,"ok",false,"message",t.status.equals("USED")?"Đã vào rạp trước đó.":"Vé không còn hiệu lực."));continue;}
            try{checkin.checkIn(user.id(),t.id,t.showtimeId);results.add(Map.of("seat",seat,"ok",true,"message","Đã xác nhận vào rạp."));}catch(IllegalArgumentException e){results.add(Map.of("seat",seat,"ok",false,"message",e.getMessage()));}}
        return Map.of("results",results,"order",reads.order(db.get(Order.class,id)));
    }
    @GetMapping("/api/staff/counter/customers") @ResponseBody public Map<String,Object> customers(@AuthenticationPrincipal SessionUser user,@RequestParam(defaultValue="")String q){return Map.of("customers",reads.customers(user.id(),q));}
    @GetMapping("/staff/counter/orders/{id}/print") public String print(@AuthenticationPrincipal SessionUser user,@PathVariable Long id,Model model){model.addAttribute("order",reads.print(user.id(),id));model.addAttribute("title","In vé · CinePrime");return "counter-print";}
    @GetMapping("/staff/counter/tickets/{id}/qr.png") public ResponseEntity<byte[]> qr(@AuthenticationPrincipal SessionUser user,@PathVariable Long id)throws Exception{Ticket t=db.get(Ticket.class,id);reads.owned(user.id(),t.orderId);if(!t.status.equals("VALID")&&!t.status.equals("USED"))throw new IllegalArgumentException("Vé không còn hiệu lực.");return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).body(files.qr(t));}
}
