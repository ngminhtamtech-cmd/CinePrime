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
        if(!Objects.equals(s.id,selectedShowtime))throw new IllegalArgumentException("Vé không đúng suất đang soát.");
        if(!t.status.equals("VALID"))throw new IllegalArgumentException("Vé đã sử dụng hoặc không còn hiệu lực.");
        if(!db.get(Order.class,t.orderId).status.equals("PAID")||s.status.equals("CANCELLED"))throw new IllegalArgumentException("Vé không hợp lệ.");
        Instant now=clock.instant();if(now.isBefore(s.startsAt.minusSeconds(3600))||now.isAfter(s.startsAt.plusSeconds(1800)))throw new IllegalArgumentException("Ngoài khung check-in: từ 60 phút trước đến 30 phút sau giờ chiếu.");
        t.status="USED";t.checkedInAt=now;t.checkedInBy=staffId;db.audit(staffId,"CHECK_IN",t.code);return t;
    }
}
