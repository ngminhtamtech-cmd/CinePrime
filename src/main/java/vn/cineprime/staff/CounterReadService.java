package vn.cineprime.staff;

import java.time.*;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.cineprime.admin.ReportService;
import vn.cineprime.booking.BookingService;
import vn.cineprime.domain.Store;
import static vn.cineprime.domain.Domain.*;

/** Read models for the box-office screen; never mutates holds or payments. */
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class CounterReadService {
    private final Store db;private final BookingService bookings;private final CounterService counter;private final CheckInService checkin;private final Clock clock;
    public CounterReadService(Store db,BookingService bookings,CounterService counter,CheckInService checkin,Clock clock){this.db=db;this.bookings=bookings;this.counter=counter;this.checkin=checkin;this.clock=clock;}
    private User staff(Long id){User u=db.get(User.class,id);if(!u.enabled||!u.role.equals("STAFF"))throw new AccessDeniedException("Tài khoản không có quyền bán vé.");return u;}
    public Map<String,Object> page(Long staffId,LocalDate day){
        User staff=staff(staffId);if(staff.cinemaId==null)return Map.of("unassigned",true,"staffCinema","Chưa phân công rạp","operationsRole","Bán vé tại quầy");
        Instant now=clock.instant();LocalDate today=now.atZone(ReportService.ZONE).toLocalDate();if(day==null||day.isBefore(today))day=today;
        Map<String,Object> model=new LinkedHashMap<>();model.put("day",day.toString());model.put("today",today.toString());model.put("staffCinema",db.get(Cinema.class,staff.cinemaId).name);model.put("operationsRole","Bán vé tại quầy");
        Optional<Shift> shift=counter.openShift(staffId);model.put("shift",shift.map(this::shift).orElse(null));model.put("hasShift",shift.isPresent());
        var shows=shows(staff.cinemaId,day,now);model.put("shows",shows);model.put("dayText",String.format("%02d/%02d",day.getDayOfMonth(),day.getMonthValue()));
        // Counter column 1 lists showtimes by movie (first show first), each movie with its time buttons.
        Map<Object,List<Map<String,Object>>> byMovie=new LinkedHashMap<>();shows.forEach(s->byMovie.computeIfAbsent(s.get("movieTitle"),k->new ArrayList<>()).add(s));
        model.put("showGroups",byMovie.values().stream().map(list->{Map<String,Object> f=list.getFirst();return Map.<String,Object>of("movieTitle",f.get("movieTitle"),"meta",f.get("ageRating")+" · "+f.get("format")+" · "+f.get("duration")+" phút","shows",list,"anySellable",list.stream().anyMatch(s->Boolean.TRUE.equals(s.get("sellable"))));}).toList());
        model.put("products",db.list(Product.class,"where e.active=true order by e.price,e.id").stream().map(p->Map.<String,Object>of("id",p.id,"name",p.name,"price",p.price,"priceText",db.cash(p.price),"description",Objects.toString(p.description,""))).toList());
        Order active=db.first(Order.class,"where e.soldBy=?1 and e.channel='COUNTER' and e.status='PENDING_PAYMENT' and e.expiresAt>?2 order by e.id desc",staffId,now).orElse(null);
        model.put("activeOrder",active==null?null:order(active));
        model.put("orders",shift.map(s->orders(staffId,s.id,now)).orElse(List.of()));model.put("lastClosed",db.first(Shift.class,"where e.staffId=?1 and e.status='CLOSED' order by e.closedAt desc",staffId).map(this::shift).orElse(null));
        model.put("serverTime",now.toString());return model;
    }
    private List<Map<String,Object>> shows(Long cinemaId,LocalDate day,Instant now){
        var rows=db.query("select s,m.title,m.ageRating,m.poster,r.name,m.duration from %s s, %s m, %s r where s.movieId=m.id and s.roomId=r.id and r.cinemaId=?1 and s.startsAt>=?2 and s.startsAt<?3 and s.status='OPEN' and m.active=true and r.active=true order by s.startsAt,s.id".formatted(db.entityName(Showtime.class),db.entityName(Movie.class),db.entityName(Room.class)),Object[].class,cinemaId,day.atStartOfDay(ReportService.ZONE).toInstant(),day.plusDays(1).atStartOfDay(ReportService.ZONE).toInstant());
        Map<Long,long[]> counts=new HashMap<>();if(!rows.isEmpty())db.query("select i.showtimeId,i.status,i.expiresAt,seat.active from %s i, %s seat where i.seatId=seat.id and i.showtimeId in ?1".formatted(db.entityName(Inventory.class),db.entityName(Seat.class)),Object[].class,rows.stream().map(r->((Showtime)r[0]).id).toList()).forEach(r->{long[] c=counts.computeIfAbsent((Long)r[0],k->new long[2]);if(!(Boolean)r[3])return;c[1]++;String st=(String)r[1];if(st.equals("AVAILABLE")||st.equals("HELD")&&!now.isBefore((Instant)r[2]))c[0]++;});
        return rows.stream().map(r->{Showtime s=(Showtime)r[0];long[] c=counts.getOrDefault(s.id,new long[2]);boolean sellable=!now.plusSeconds(300).isAfter(s.startsAt);ZonedDateTime t=s.startsAt.atZone(ReportService.ZONE);
            Map<String,Object> v=new LinkedHashMap<>();v.put("id",s.id);v.put("movieTitle",r[1]);v.put("ageRating",r[2]);v.put("poster",Objects.toString(r[3],""));v.put("roomName",r[4]);v.put("time",String.format("%02d:%02d",t.getHour(),t.getMinute()));v.put("when",db.date(s.startsAt));v.put("format",s.format+" · "+s.language);v.put("duration",r[5]);v.put("available",c[0]);v.put("capacity",c[1]);v.put("sellable",sellable&&c[0]>0);v.put("state",!sellable?"Đã ngừng bán":c[0]==0?"Hết ghế":"Còn "+c[0]+"/"+c[1]+" ghế");return v;}).toList();
    }
    public Map<String,Object> shift(Shift s){
        long cash=counter.expected(s)-s.openingCash;long orders=db.count(Order.class,"where e.shiftId=?1 and e.status='PAID'",s.id),cancelled=db.count(Order.class,"where e.shiftId=?1 and e.status='CANCELLED' and e.cancelledAt is not null",s.id);
        long tickets=db.query("select count(t) from %s t, %s o where t.orderId=o.id and o.shiftId=?1 and t.status in ('VALID','USED')".formatted(db.entityName(Ticket.class),db.entityName(Order.class)),Long.class,s.id).getFirst();
        Map<String,Object> v=new LinkedHashMap<>();v.put("id",s.id);v.put("status",s.status);v.put("opened",db.date(s.openedAt));v.put("openedTime",db.date(s.openedAt).substring(0,5));v.put("closed",db.date(s.closedAt));v.put("openingCash",db.cash(s.openingCash));v.put("cashIn",db.cash(cash));v.put("orders",orders);v.put("cancelled",cancelled);v.put("tickets",tickets);
        long expected=s.expectedCash!=null?s.expectedCash:counter.expected(s);v.put("expected",db.cash(expected));v.put("expectedValue",expected);v.put("counted",s.countedCash==null?"—":db.cash(s.countedCash));v.put("difference",s.countedCash==null?"—":(s.countedCash-expected>0?"+":"")+db.cash(s.countedCash-expected));v.put("balanced",s.countedCash==null||s.countedCash==expected);v.put("note",Objects.toString(s.note,""));
        User u=db.get(User.class,s.staffId);v.put("staffName",u.name);v.put("cinemaName",db.get(Cinema.class,s.cinemaId).name);return v;
    }
    private List<Map<String,Object>> orders(Long staffId,Long shiftId,Instant now){
        return db.list(Order.class,"where e.soldBy=?1 and e.shiftId=?2 and e.status in ('PAID','CANCELLED') and e.paidAt is not null order by e.paidAt desc,e.id desc",staffId,shiftId).stream().map(o->{Map<String,Object> v=summary(o);boolean anyUsed=db.count(Ticket.class,"where e.orderId=?1 and e.status='USED'",o.id)>0;
            boolean cancellable=o.status.equals("PAID")&&!anyUsed&&now.isBefore(o.showStartsAt)&&!now.isAfter(o.paidAt.plusSeconds(CounterService.CANCEL_WINDOW));v.put("cancellable",cancellable);v.put("cancelUntil",db.date(o.paidAt.plusSeconds(CounterService.CANCEL_WINDOW)));return v;}).toList();
    }
    private Map<String,Object> summary(Order o){
        Map<String,Object> v=new LinkedHashMap<>();v.put("id",o.id);v.put("code",o.code);v.put("status",o.status);v.put("statusLabel",switch(o.status){case "PAID"->"Đã thu tiền";case "CANCELLED"->"Đã hủy";case "EXPIRED"->"Hết giữ ghế";case "PENDING_PAYMENT"->"Đang giữ ghế";default->o.status;});
        v.put("movieTitle",o.movieTitle);v.put("roomName",o.roomName);v.put("cinemaName",o.cinemaName);v.put("when",db.date(o.showStartsAt));v.put("paid",db.date(o.paidAt));v.put("total",o.total);v.put("totalText",db.cash(o.total));
        v.put("seats",String.join(", ",db.list(OrderSeat.class,"where e.orderId=?1 order by e.label",o.id).stream().map(l->l.label).toList()));v.put("cancelReason",Objects.toString(o.cancelReason,""));
        v.put("customer",o.userId==null?"Khách vãng lai":masked(db.get(User.class,o.userId)).get("label"));return v;
    }
    public Map<String,Object> order(Order o){
        Map<String,Object> v=summary(o);v.put("expiresAt",o.expiresAt.toString());v.put("showtimeId",o.showtimeId);v.put("subtotal",o.subtotal);v.put("subtotalText",db.cash(o.subtotal));
        v.put("seatLines",db.list(OrderSeat.class,"where e.orderId=?1 order by e.label",o.id).stream().map(l->Map.<String,Object>of("label",l.label,"seatType",l.seatType,"type",vn.cineprime.admin.AdminPresentation.text(l.seatType),"price",l.price,"priceText",db.cash(l.price))).toList());
        v.put("productLines",db.list(OrderProduct.class,"where e.orderId=?1 order by e.id",o.id).stream().map(l->Map.<String,Object>of("productId",l.productId,"name",l.name,"quantity",l.quantity,"price",l.price,"amountText",db.cash(l.price*l.quantity))).toList());
        Optional<Payment> cash=db.first(Payment.class,"where e.orderId=?1 and e.provider='CASH' order by e.id desc",o.id);v.put("received",cash.map(p->db.cash(p.received==null?p.amount:p.received)).orElse("—"));v.put("change",cash.map(p->db.cash((p.received==null?p.amount:p.received)-p.amount)).orElse("—"));
        v.put("tickets",db.query("select t,seat.label from %s t, %s seat where t.seatId=seat.id and t.orderId=?1 order by seat.label".formatted(db.entityName(Ticket.class),db.entityName(Seat.class)),Object[].class,o.id).stream().map(r->{Ticket t=(Ticket)r[0];return Map.<String,Object>of("id",t.id,"code",t.code,"seat",r[1],"status",t.status,"statusLabel",switch(t.status){case "USED"->"Đã vào rạp";case "VOID"->"Vô hiệu";default->"Chưa vào";});}).toList());
        return v;
    }
    public Order owned(Long staffId,Long id){staff(staffId);Order o=db.get(Order.class,id);if(!"COUNTER".equals(o.channel)||!Objects.equals(o.soldBy,staffId))throw new AccessDeniedException("Đơn không phải đơn quầy của bạn.");return o;}
    public Map<String,Object> orderView(Long staffId,Long id){Order o=owned(staffId,id);if(o.status.equals("PENDING_PAYMENT")&&!clock.instant().isBefore(o.expiresAt))return Map.of("order",Map.of("id",o.id,"status","EXPIRED","statusLabel","Hết giữ ghế"),"serverTime",clock.instant().toString());return Map.of("order",order(o),"serverTime",clock.instant().toString());}
    public Map<String,Object> print(Long staffId,Long id){Order o=owned(staffId,id);if(!o.status.equals("PAID"))throw new IllegalArgumentException("Chỉ in vé của đơn đã thu tiền.");Map<String,Object> v=order(o);v.put("cashier",db.get(User.class,staffId).name);Showtime s=db.get(Showtime.class,o.showtimeId);v.put("format",s.format+" · "+s.language);v.put("printedAt",db.date(clock.instant()));return v;}
    public Map<String,Object> seats(Long staffId,Long showtimeId){
        Showtime show=db.get(Showtime.class,showtimeId);checkin.permitted(staffId,show);Instant now=clock.instant();Map<String,Long> prices=new HashMap<>();
        List<Map<String,Object>> list=db.query("select i,s from %s i, %s s where i.seatId=s.id and i.showtimeId=?1 order by i.seatId".formatted(db.entityName(Inventory.class),db.entityName(Seat.class)),Object[].class,showtimeId).stream().map(row->{Inventory i=(Inventory)row[0];Seat seat=(Seat)row[1];String state=!seat.active?"MAINTENANCE":i.status.equals("HELD")&&!now.isBefore(i.expiresAt)?"AVAILABLE":i.status;
            Map<String,Object> v=new LinkedHashMap<>();v.put("id",seat.id);v.put("label",seat.label);v.put("rowLabel",seat.rowLabel);v.put("position",seat.position);v.put("type",seat.type);v.put("coupleGroup",seat.coupleGroup);v.put("roomId",seat.roomId);v.put("state",state);v.put("mine",i.orderId!=null&&state.equals("HELD")&&db.get(Order.class,i.orderId).soldBy!=null&&Objects.equals(db.get(Order.class,i.orderId).soldBy,staffId));
            long price=seat.active?prices.computeIfAbsent(seat.type,t->bookings.price(show,seat)):0;v.put("price",price);v.put("priceText",seat.active?db.cash(price):"Bảo trì");return v;}).toList();
        Room room=db.get(Room.class,show.roomId);Movie m=db.get(Movie.class,show.movieId);
        return Map.of("seats",list,"serverTime",now.toString(),"show",Map.of("id",show.id,"movieTitle",m.title,"roomName",room.name,"when",db.date(show.startsAt),"startsAt",show.startsAt.toString(),"sellable",show.status.equals("OPEN")&&!now.plusSeconds(300).isAfter(show.startsAt)));
    }
    /** Exact phone or email match only; the counter sees a masked name and contact. */
    public List<Map<String,Object>> customers(Long staffId,String raw){
        staff(staffId);String q=raw==null?"":raw.trim();if(q.length()<6||q.length()>120)throw new IllegalArgumentException("Nhập đầy đủ số điện thoại hoặc email của khách.");
        String phone=q.replaceAll("[\\s.-]","");
        List<User> found=q.contains("@")?db.list(User.class,"where lower(e.email)=?1 and e.role='CUSTOMER' and e.enabled=true",q.toLowerCase(Locale.ROOT)):db.list(User.class,"where e.phone=?1 and e.role='CUSTOMER' and e.enabled=true order by e.id",phone);
        return found.stream().limit(5).map(this::masked).toList();
    }
    private Map<String,Object> masked(User u){String name=Objects.toString(u.name,"");String[] parts=name.trim().split("\\s+");String shown=parts.length==0||parts[0].isEmpty()?"Khách":parts.length==1?parts[0]:"… "+parts[parts.length-1];
        String email=u.email==null?"":u.email.replaceAll("(?<=^.).*(?=@)","***");String phone=u.phone==null||u.phone.length()<4?"":"***"+u.phone.substring(u.phone.length()-3);
        return Map.of("id",u.id,"name",shown,"contact",phone.isEmpty()?email:phone+" · "+email,"label",shown+" · "+(phone.isEmpty()?email:phone));}
}
