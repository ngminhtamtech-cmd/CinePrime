package vn.cineprime.staff;

import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cineprime.booking.BookingService;
import vn.cineprime.domain.Store;
import vn.cineprime.notification.Notifications;
import static vn.cineprime.domain.Domain.*;

/**
 * Box-office cash sales. Lock order: Staff user → Shift → Showtime → Cinema → Inventory → Order → Payment → Ticket.
 * Counter holds follow the online rules (5 minutes, ≤ 8 seats, couples as pairs) but sales close at the start time.
 */
@Service @Transactional
public class CounterService {
    public static final long CANCEL_WINDOW=900,MAX_CASH=100_000_000L;
    private static final Pattern KEY=Pattern.compile("[A-Za-z0-9-]{8,64}");
    private final Store db;private final BookingService bookings;private final CheckInService checkin;private final Notifications notices;private final Clock clock;
    public CounterService(Store db,BookingService bookings,CheckInService checkin,Notifications notices,Clock clock){this.db=db;this.bookings=bookings;this.checkin=checkin;this.notices=notices;this.clock=clock;}
    private Instant now(){return clock.instant();}
    private User seller(Long staffId){User u=db.lock(User.class,staffId);if(!u.enabled||!u.role.equals("STAFF"))throw new AccessDeniedException("Tài khoản không có quyền bán vé.");if(u.cinemaId==null)throw new IllegalArgumentException("Bạn chưa được phân công rạp. Liên hệ Admin trước khi bán vé.");return u;}
    public Optional<Shift> openShift(Long staffId){return db.first(Shift.class,"where e.staffId=?1 and e.status='OPEN'",staffId);}
    private Shift activeShift(User staff){Shift s=db.lock(Shift.class,openShift(staff.id).orElseThrow(()->new IllegalArgumentException("Hãy mở ca trước khi bán vé.")).id);if(!s.status.equals("OPEN"))throw new IllegalArgumentException("Ca đã đóng. Hãy mở ca mới.");if(!s.cinemaId.equals(staff.cinemaId))throw new IllegalArgumentException("Ca đang mở thuộc rạp cũ. Hãy đóng ca rồi mở ca mới tại rạp hiện tại.");return s;}
    private static long money(Long value,String field){if(value==null||value<0||value>MAX_CASH)throw new IllegalArgumentException(field+" phải là số nguyên đồng từ 0 đến 100.000.000.");return value;}

    public Shift open(Long staffId,Long openingCash){
        User staff=seller(staffId);if(openShift(staffId).isPresent())throw new IllegalArgumentException("Bạn đang có ca mở. Đóng ca hiện tại trước khi mở ca mới.");
        Shift s=new Shift();s.staffId=staffId;s.cinemaId=staff.cinemaId;s.openingCash=money(openingCash,"Tiền đầu ca");s.openedAt=now();db.save(s);db.audit(staffId,"SHIFT_OPEN","#"+s.id+" · "+s.openingCash);return s;
    }
    /** Cash that should be in the drawer: opening float plus successful cash payments (refunded ones are excluded). */
    public long expected(Shift s){return Math.addExact(s.openingCash,db.query("select coalesce(sum(p.amount),0) from "+db.entityName(Payment.class)+" p where p.shiftId=?1 and p.provider='CASH' and p.status='SUCCEEDED'",Long.class,s.id).getFirst());}
    public Shift close(Long staffId,Long counted,String note){
        User staff=db.lock(User.class,staffId);Shift s=db.lock(Shift.class,openShift(staff.id).orElseThrow(()->new IllegalArgumentException("Bạn không có ca đang mở.")).id);
        for(Order snapshot:db.list(Order.class,"where e.soldBy=?1 and e.channel='COUNTER' and e.status='PENDING_PAYMENT' order by e.showtimeId,e.id",staffId)){
            if(now().isBefore(snapshot.expiresAt))throw new IllegalArgumentException("Còn đơn đang giữ ghế. Thu tiền hoặc hủy giữ ghế trước khi đóng ca.");
            db.lock(Showtime.class,snapshot.showtimeId);Order o=db.lock(Order.class,snapshot.id);if(o.status.equals("PENDING_PAYMENT"))bookings.release(o,"EXPIRED");
        }
        long count=money(counted,"Tiền đếm được"),expect=expected(s);String text=note==null?"":note.trim();
        if(text.length()>500)throw new IllegalArgumentException("Ghi chú tối đa 500 ký tự.");
        if(count!=expect&&text.isEmpty())throw new IllegalArgumentException("Tiền đếm lệch "+db.money(count-expect)+" so với hệ thống. Nhập ghi chú giải thích trước khi đóng ca.");
        s.status="CLOSED";s.closedAt=now();s.expectedCash=expect;s.countedCash=count;s.note=text.isEmpty()?null:text;db.audit(staffId,"SHIFT_CLOSE","#"+s.id+" · dự kiến "+expect+" · đếm "+count);return s;
    }

    /** One active counter hold per Staff: a new hold releases the previous unpaid one. */
    public Order hold(Long staffId,Long showtimeId,List<Long> seats){
        User staff=seller(staffId);Shift shift=activeShift(staff);
        Showtime target=db.get(Showtime.class,showtimeId);checkin.permitted(staffId,target);
        for(Order snapshot:db.list(Order.class,"where e.soldBy=?1 and e.channel='COUNTER' and e.status='PENDING_PAYMENT' order by e.showtimeId,e.id",staffId)){
            db.lock(Showtime.class,snapshot.showtimeId);Order old=db.lock(Order.class,snapshot.id);if(old.status.equals("PENDING_PAYMENT"))bookings.release(old,now().isBefore(old.expiresAt)?"CANCELLED":"EXPIRED");
        }
        Showtime show=db.lock(Showtime.class,showtimeId);
        if(!show.status.equals("OPEN"))throw new IllegalArgumentException("Suất không còn mở bán.");
        if(now().plusSeconds(300).isAfter(show.startsAt))throw new IllegalArgumentException("Còn dưới 5 phút tới giờ chiếu nên không thể giữ ghế. Quầy ngừng bán suất này.");
        Order o=new Order();o.channel="COUNTER";o.soldBy=staffId;o.shiftId=shift.id;Order held=bookings.reserve(show,seats,o);
        db.audit(staffId,"COUNTER_HOLD",held.code);return held;
    }
    private Order ownCounterOrder(Long staffId,Long id){Order o=db.get(Order.class,id);if(!"COUNTER".equals(o.channel)||!Objects.equals(o.soldBy,staffId))throw new AccessDeniedException("Đơn không phải đơn quầy của bạn.");return o;}
    private void editable(Order o){if(!o.status.equals("PENDING_PAYMENT")||!now().isBefore(o.expiresAt))throw new IllegalArgumentException("Hết thời gian giữ ghế. Hãy chọn ghế lại.");}
    public Order combos(Long staffId,Long id,Map<Long,Integer> quantities){
        User staff=seller(staffId);ownCounterOrder(staffId,id);activeShift(staff);db.lock(Showtime.class,db.get(Order.class,id).showtimeId);Order o=db.lock(Order.class,id);editable(o);
        for(OrderProduct old:db.list(OrderProduct.class,"where e.orderId=?1",id))db.delete(old);db.entityManager().flush();
        long subtotal=db.list(OrderSeat.class,"where e.orderId=?1",id).stream().mapToLong(l->l.price).sum();
        for(var e:new TreeMap<>(quantities).entrySet()){
            Integer n=e.getValue();if(n==null||n==0)continue;if(n<0||n>20)throw new IllegalArgumentException("Số lượng combo từ 0 đến 20.");
            Product p=db.get(Product.class,e.getKey());if(!p.active)throw new IllegalArgumentException("Combo "+p.name+" đã ngừng bán.");
            OrderProduct line=new OrderProduct();line.orderId=id;line.productId=p.id;line.name=p.name;line.quantity=n;line.price=p.price;db.save(line);subtotal=Math.addExact(subtotal,Math.multiplyExact(p.price,(long)n));
        }
        o.subtotal=subtotal;o.discount=0;o.total=subtotal;return o;
    }
    public void release(Long staffId,Long id){User staff=seller(staffId);ownCounterOrder(staffId,id);db.lock(Showtime.class,db.get(Order.class,id).showtimeId);Order o=db.lock(Order.class,id);if(o.status.equals("PENDING_PAYMENT"))bookings.release(o,now().isBefore(o.expiresAt)?"CANCELLED":"EXPIRED");db.audit(staff.id,"COUNTER_RELEASE",o.code);}

    /** Cash payment; repeating the same request key after success returns the same sale instead of charging twice. */
    public Order pay(Long staffId,Long id,Long received,Long customerId,String requestKey){
        if(requestKey==null||!KEY.matcher(requestKey).matches())throw new IllegalArgumentException("Thiếu mã yêu cầu thu tiền. Tải lại trang rồi thử lại.");
        User staff=seller(staffId);ownCounterOrder(staffId,id);Shift shift=activeShift(staff);
        db.lock(Showtime.class,db.get(Order.class,id).showtimeId);Order o=db.lock(Order.class,id);
        String request="cash-"+requestKey;
        if(o.status.equals("PAID")){if(db.first(Payment.class,"where e.orderId=?1 and e.requestId=?2 and e.status='SUCCEEDED'",id,request).isPresent())return o;throw new IllegalArgumentException("Đơn đã được thu tiền.");}
        editable(o);Showtime show=db.get(Showtime.class,o.showtimeId);
        if(!now().isBefore(show.startsAt))throw new IllegalArgumentException("Đã tới giờ chiếu. Quầy ngừng bán suất này.");
        if(!show.status.equals("OPEN"))throw new IllegalArgumentException("Suất không còn mở bán.");
        if(!Objects.equals(o.shiftId,shift.id))throw new IllegalArgumentException("Ghế được giữ trong ca khác. Hãy chọn ghế lại.");
        long cash=money(received,"Tiền khách đưa");if(cash<o.total)throw new IllegalArgumentException("Tiền khách đưa chưa đủ. Còn thiếu "+db.money(o.total-cash)+".");
        User customer=null;if(customerId!=null){customer=db.get(User.class,customerId);if(!customer.role.equals("CUSTOMER")||!customer.enabled)throw new IllegalArgumentException("Tài khoản khách không hợp lệ hoặc đã khóa.");}
        List<Inventory> seats=db.list(Inventory.class,"where e.orderId=?1 order by e.seatId",o.id);for(Inventory inv:seats)db.lock(Inventory.class,inv.id);
        if(seats.size()!=db.count(OrderSeat.class,"where e.orderId=?1",o.id)||seats.stream().anyMatch(i->!i.status.equals("HELD")))throw new IllegalArgumentException("Ghế không còn được giữ cho đơn này. Hãy chọn ghế lại.");
        Payment p=new Payment();p.orderId=o.id;p.provider="CASH";p.requestId=request;p.externalOrderId="CASH-"+o.code;p.amount=o.total;p.received=cash;p.shiftId=shift.id;p.status="SUCCEEDED";p.resultCode=0;p.createdAt=now();p.updatedAt=now();db.save(p);
        o.frozen=true;if(customer!=null)o.userId=customer.id;bookings.issue(o,seats);
        if(customer!=null){Point points=new Point();points.userId=customer.id;points.orderId=o.id;points.points=o.total/1000;points.createdAt=now();db.save(points);
            notices.notify(customer,"paid:"+o.id,"Mua vé tại quầy thành công · "+o.code,"Phim: "+o.movieTitle+"\nRạp: "+o.cinemaName+" · "+o.roomName+"\nSuất: "+db.date(o.showStartsAt)+"\nVé đã có trong Vé của tôi trên CinePrime.",true);}
        db.audit(staffId,"COUNTER_SALE",o.code+" · "+o.total+(customer==null?" · khách vãng lai":" · khách #"+customer.id));return o;
    }

    /** Correction of a mistaken sale: own cash order, current open shift, nobody checked in, before the show, within 15 minutes. */
    public Order cancel(Long staffId,Long id,String reason){
        String text=reason==null?"":reason.trim();if(text.isEmpty())throw new IllegalArgumentException("Nhập lý do hủy đơn.");if(text.length()>500)throw new IllegalArgumentException("Lý do tối đa 500 ký tự.");
        User staff=seller(staffId);ownCounterOrder(staffId,id);Shift shift=activeShift(staff);
        Showtime show=db.lock(Showtime.class,db.get(Order.class,id).showtimeId);Order o=db.lock(Order.class,id);
        if(!o.status.equals("PAID"))throw new IllegalArgumentException("Chỉ hủy được đơn quầy đã thu tiền.");
        if(!Objects.equals(o.shiftId,shift.id))throw new IllegalArgumentException("Chỉ hủy được đơn bán trong ca đang mở.");
        if(!now().isBefore(show.startsAt))throw new IllegalArgumentException("Đã tới giờ chiếu, không thể hủy đơn.");
        if(now().isAfter(o.paidAt.plusSeconds(CANCEL_WINDOW)))throw new IllegalArgumentException("Đã quá 15 phút kể từ lúc bán, không thể hủy tại quầy. Liên hệ quản lý.");
        List<Ticket> tickets=db.list(Ticket.class,"where e.orderId=?1 order by e.id",id);for(Ticket t:tickets){db.lock(Ticket.class,t.id);if(!t.status.equals("VALID"))throw new IllegalArgumentException("Vé "+t.code+" đã check-in, không thể hủy đơn.");}
        for(Inventory inv:db.list(Inventory.class,"where e.orderId=?1 order by e.seatId",id)){db.lock(Inventory.class,inv.id);inv.status="AVAILABLE";inv.orderId=null;inv.expiresAt=null;}
        for(Ticket t:tickets)t.status="VOID";
        for(Payment p:db.list(Payment.class,"where e.orderId=?1 and e.provider='CASH' and e.status='SUCCEEDED'",id)){db.lock(Payment.class,p.id);p.status="REFUNDED";p.updatedAt=now();}
        if(o.userId!=null)db.first(Point.class,"where e.orderId=?1 and e.kind='EARN'",id).ifPresent(earn->{Point back=new Point();back.userId=earn.userId;back.orderId=id;back.kind="REVERSAL";back.points=-earn.points;back.createdAt=now();db.save(back);notices.notify(db.get(User.class,o.userId),"cancel:"+id,"Đơn tại quầy đã hủy · "+o.code,"Đơn "+o.code+" ("+o.movieTitle+") đã được nhân viên hủy và hoàn tiền mặt tại quầy. Điểm của đơn đã được trừ.",false);});
        o.status="CANCELLED";o.cancelledAt=now();o.cancelledBy=staffId;o.cancelReason=text;db.audit(staffId,"COUNTER_CANCEL",o.code+" · "+text);return o;
    }
}
