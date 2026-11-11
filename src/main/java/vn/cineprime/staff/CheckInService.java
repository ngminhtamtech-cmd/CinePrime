package vn.cineprime.staff;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cineprime.domain.Store;
import static vn.cineprime.domain.Domain.*;

@Service @Transactional
public class CheckInService {
    private final Store db;private final Clock clock;
    public CheckInService(Store db,Clock clock){this.db=db;this.clock=clock;}
    public void permitted(Long staffId,Showtime s){User staff=db.get(User.class,staffId);Room r=db.get(Room.class,s.roomId);if(!staff.enabled||!staff.role.equals("STAFF")||!Objects.equals(r.cinemaId,staff.cinemaId))throw new org.springframework.security.access.AccessDeniedException("Vé không thuộc rạp bạn phụ trách.");}
    public List<Ticket> lookup(Long staffId,String raw){
        String value=raw.trim();if(value.length()>200)throw new IllegalArgumentException("Mã vé không hợp lệ.");
        List<Ticket> tickets;
        if(value.startsWith("CP1:"))tickets=db.list(Ticket.class,"where e.token=?1",value.substring(4));
        else if(value.startsWith("CP-")){Order o=db.first(Order.class,"where e.code=?1",value).orElseThrow(()->new IllegalArgumentException("Không tìm thấy đơn."));tickets=db.list(Ticket.class,"where e.orderId=?1",o.id);}
        else tickets=db.list(Ticket.class,"where e.code=?1",value);
        if(tickets.isEmpty())throw new IllegalArgumentException("Không tìm thấy vé đã phát hành.");
        for(Ticket t:tickets)permitted(staffId,db.get(Showtime.class,t.showtimeId));return tickets;
    }
    public Ticket checkIn(Long staffId,Long ticketId,Long selectedShowtime){
        Ticket t=db.lock(Ticket.class,ticketId);Showtime s=db.get(Showtime.class,t.showtimeId);permitted(staffId,s);
        Instant now=clock.instant();Eligibility eligibility=eligibility(t,s,db.get(Order.class,t.orderId),selectedShowtime,now);
        if(!eligibility.allowed())throw new StaffProblem(eligibility.code(),eligibility.message());
        t.status="USED";t.checkedInAt=now;t.checkedInBy=staffId;db.audit(staffId,"CHECK_IN",t.code);return t;
    }
    public record Eligibility(boolean allowed,String code,String message){}
    public static class StaffProblem extends IllegalArgumentException {
        private final String code;public StaffProblem(String code,String message){super(message);this.code=code;}public String code(){return code;}
    }
    public static Eligibility eligibility(Ticket t,Showtime s,Order order,Long selected,Instant now){
        if(!Objects.equals(s.id,selected))return new Eligibility(false,"WRONG_SHOW","Vé không đúng suất đang soát.");
        if(t.status.equals("USED"))return new Eligibility(false,"USED","Vé đã sử dụng. Kiểm tra thời điểm vào rạp.");
        if(!t.status.equals("VALID")||!order.status.equals("PAID")||s.status.equals("CANCELLED"))return new Eligibility(false,"VOID","Vé không còn hiệu lực.");
        if(now.isBefore(s.startsAt.minusSeconds(3600))||now.isAfter(s.startsAt.plusSeconds(1800)))return new Eligibility(false,"OUTSIDE_WINDOW","Ngoài khung check-in: từ 60 phút trước đến 30 phút sau giờ chiếu.");
        return new Eligibility(true,"READY","Vé hợp lệ · Có thể xác nhận vào rạp.");
    }
}
