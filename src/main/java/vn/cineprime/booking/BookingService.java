package vn.cineprime.booking;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cineprime.auth.AccountService;
import vn.cineprime.domain.Store;
import vn.cineprime.notification.Notifications;
import static vn.cineprime.domain.Domain.*;

@Service @Transactional
public class BookingService {
    private final Store db;private final Clock clock;private final Notifications notices;
    public BookingService(Store db,Clock clock,Notifications notices){this.db=db;this.clock=clock;this.notices=notices;}
    public Instant now(){return clock.instant();}
    public long price(Showtime show,Seat seat){
        Room room=db.get(Room.class,show.roomId);
        ZonedDateTime local=show.startsAt.atZone(ZoneId.of("Asia/Ho_Chi_Minh"));
        String day=local.getDayOfWeek().getValue()>=6?"WEEKEND":"WEEKDAY";
        int minute=local.getHour()*60+local.getMinute();
        List<Price> matches=db.list(Price.class,"where e.cinemaId=?1 and e.seatType=?2 and e.dayType=?3 and e.fromMinute<=?4 and e.toMinute>?4",room.cinemaId,seat.type,day,minute);
        if(matches.size()!=1)throw new IllegalArgumentException("Bảng giá chưa đầy đủ hoặc trùng khung giờ.");
        return Math.addExact(matches.getFirst().amount,show.surcharge);
    }
    public Order hold(Long userId,Long showtimeId,List<Long> requested){
        if(requested==null||requested.isEmpty()||requested.size()>8||new HashSet<>(requested).size()!=requested.size())throw new IllegalArgumentException("Chọn từ 1 đến 8 ghế, không lặp.");
        User user=db.lock(User.class,userId);
        if(!user.enabled)throw new IllegalArgumentException("Tài khoản đã khóa.");
            for(Order old:db.list(Order.class,"where e.userId=?1 and e.status='PENDING_PAYMENT' order by e.showtimeId,e.id",userId)){
                if(now().isBefore(old.expiresAt))throw new IllegalArgumentException("Bạn còn đơn đang giữ ghế. Vui lòng hoàn tất hoặc hủy đơn trước.");
                expireLocked(old);
        }
        Showtime show=db.lock(Showtime.class,showtimeId);
        if(!show.status.equals("OPEN")||now().plusSeconds(1200).isAfter(show.startsAt))throw new IllegalArgumentException("Suất đã ngừng nhận đặt vé.");
        Order o=new Order();o.userId=userId;return reserve(show,requested,o);
    }
    /** Allocates seats for an already locked OPEN showtime; caller has checked its own sales cutoff and hold limits. */
    public Order reserve(Showtime show,List<Long> requested,Order o){
        if(requested==null||requested.isEmpty()||requested.size()>8||new HashSet<>(requested).size()!=requested.size())throw new IllegalArgumentException("Chọn từ 1 đến 8 ghế, không lặp.");
        Movie movie=db.lockRead(Movie.class,show.movieId);Room room=db.get(Room.class,show.roomId);Cinema cinema=db.get(Cinema.class,room.cinemaId);
        if(!movie.active||vn.cineprime.domain.MovieLifecycle.afterEnd(movie,show.startsAt)||!room.active||!cinema.active)throw new IllegalArgumentException("Suất không còn mở bán.");
        db.lock(Cinema.class,cinema.id);
        List<Seat> seats=requested.stream().sorted().map(id->db.get(Seat.class,id)).toList();
        Set<Long> selected=new HashSet<>(requested);
        for(Seat seat:seats){
            if(!seat.roomId.equals(room.id)||!seat.active)throw new IllegalArgumentException("Ghế không hợp lệ hoặc đang bảo trì.");
            if(seat.type.equals("COUPLE")){
                List<Seat> pair=db.list(Seat.class,"where e.roomId=?1 and e.coupleGroup=?2",room.id,seat.coupleGroup);
                if(pair.size()!=2||pair.stream().anyMatch(s->!s.active||!selected.contains(s.id)))throw new IllegalArgumentException("Ghế đôi phải chọn đủ cả cặp.");
            }
        }
        // Showtime lock serializes allocation/expiry and avoids cyclic locks between expired owners.
        List<Inventory> inventories=new ArrayList<>();
        for(Seat seat:seats){
            Inventory inv=db.first(Inventory.class,"where e.showtimeId=?1 and e.seatId=?2",show.id,seat.id).orElseThrow(()->new IllegalArgumentException("Sơ đồ ghế chưa được mở bán."));
            db.lock(Inventory.class,inv.id);
            if(inv.status.equals("SOLD")||(inv.status.equals("HELD")&&now().isBefore(inv.expiresAt)))throw new IllegalArgumentException("Ghế "+seat.label+" vừa được người khác giữ hoặc mua. Hãy chọn lại.");
            inventories.add(inv);
        }
        o.showtimeId=show.id;o.code="CP-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT);
        o.createdAt=now();o.expiresAt=now().plusSeconds(300);o.movieTitle=movie.title;o.cinemaName=cinema.name;o.roomName=room.name;o.showStartsAt=show.startsAt;db.save(o);
        for(int i=0;i<seats.size();i++){
            Seat seat=seats.get(i);OrderSeat line=new OrderSeat();line.orderId=o.id;line.seatId=seat.id;line.label=seat.label;line.seatType=seat.type;line.price=price(show,seat);db.save(line);o.subtotal=Math.addExact(o.subtotal,line.price);
            Inventory inv=inventories.get(i);inv.orderId=o.id;inv.status="HELD";inv.expiresAt=o.expiresAt;
        }
        o.total=o.subtotal;return o;
    }
    public Order owned(Long userId,Long id){Order o=db.get(Order.class,id);if(o.userId==null||!o.userId.equals(userId))throw new org.springframework.security.access.AccessDeniedException("Không được truy cập đơn này.");return o;}
    /** Online orders: customer → showtime → order. Counter orders: seller → showtime → order (same order as CounterService). */
    public Order lockOrder(Long id){Order snapshot=db.get(Order.class,id);Long owner=snapshot.channel.equals("COUNTER")?snapshot.soldBy:snapshot.userId;if(owner!=null)db.lock(User.class,owner);db.lock(Showtime.class,snapshot.showtimeId);return db.lock(Order.class,id);}
    private void editable(Order o){if(!o.status.equals("PENDING_PAYMENT")||!now().isBefore(o.expiresAt))throw new IllegalArgumentException("Đơn đã hết hạn hoặc đã hoàn tất.");if(o.frozen)throw new IllegalArgumentException("Đơn đã chuyển sang thanh toán, không thể sửa nội dung.");}
    public void updateCart(Long userId,Long id,Map<Long,Integer> quantities,String voucherCode){
        owned(userId,id);Order o=lockOrder(id);editable(o);
        for(OrderProduct old:db.list(OrderProduct.class,"where e.orderId=?1",id))db.delete(old);
        o.subtotal=db.list(OrderSeat.class,"where e.orderId=?1",id).stream().mapToLong(l->l.price).sum();
        for(var e:quantities.entrySet()){
            if(e.getValue()==0)continue;if(e.getValue()<0||e.getValue()>20)throw new IllegalArgumentException("Số lượng combo từ 0 đến 20.");
            Product p=db.get(Product.class,e.getKey());if(!p.active)throw new IllegalArgumentException("Combo đã ngừng bán.");
            OrderProduct line=new OrderProduct();line.orderId=id;line.productId=p.id;line.name=p.name;line.quantity=e.getValue();line.price=p.price;db.save(line);o.subtotal=Math.addExact(o.subtotal,Math.multiplyExact(p.price,line.quantity));
        }
        Optional<VoucherUse> previous=db.first(VoucherUse.class,"where e.orderId=?1",id);
        previous.ifPresent(use->{db.lock(Voucher.class,use.voucherId);use.status="RELEASED";});
        o.voucherId=null;o.discount=0;
        if(voucherCode!=null&&!voucherCode.isBlank()){
            Voucher v=db.first(Voucher.class,"where e.code=?1",voucherCode.trim().toUpperCase(Locale.ROOT)).orElseThrow(()->new IllegalArgumentException("Mã ưu đãi không tồn tại."));db.lock(Voucher.class,v.id);
            if(!v.active||now().isBefore(v.startsAt)||!now().isBefore(v.endsAt)||o.subtotal<v.minimum)throw new IllegalArgumentException("Đơn chưa đủ điều kiện áp dụng mã.");
            List<VoucherUse> used=db.list(VoucherUse.class,"where e.voucherId=?1 and (e.status='USED' or (e.status='RESERVED' and e.expiresAt>?2))",v.id,now());
            if(used.size()>=v.quota||used.stream().filter(u->u.userId.equals(userId)).count()>=v.perUser)throw new IllegalArgumentException("Mã đã hết lượt sử dụng.");
            long discount=v.kind.equals("PERCENT")?Math.multiplyExact(o.subtotal,v.value)/100:v.value;
            if(v.maximum>0)discount=Math.min(discount,v.maximum);o.discount=Math.min(o.subtotal,discount);o.voucherId=v.id;
            VoucherUse use=previous.orElseGet(VoucherUse::new);use.orderId=id;use.voucherId=v.id;use.userId=userId;use.status="RESERVED";use.expiresAt=o.expiresAt;db.save(use);
        }
        o.total=o.subtotal-o.discount;
    }
    /** Admin cancel of an unpaid order of any channel. */
    public void cancelUnpaid(Long id){Order o=lockOrder(id);if(!o.status.equals("PENDING_PAYMENT"))throw new IllegalArgumentException("Chỉ hủy được đơn chưa thanh toán.");release(o,"CANCELLED");}
    public void cancel(Long userId,Long id){owned(userId,id);Order o=lockOrder(id);if(!o.status.equals("PENDING_PAYMENT"))throw new IllegalArgumentException("Chỉ hủy được đơn chưa thanh toán.");release(o,"CANCELLED");}
    public void expire(Long id){Order o=lockOrder(id);if(o.status.equals("PENDING_PAYMENT")&&!now().isBefore(o.expiresAt))release(o,"EXPIRED");}
    private void expireLocked(Order o){db.lock(Showtime.class,o.showtimeId);db.lock(Order.class,o.id);if(o.status.equals("PENDING_PAYMENT")&&!now().isBefore(o.expiresAt))release(o,"EXPIRED");}
    public void release(Order o,String status){
        o.status=status;
        for(Inventory i:db.list(Inventory.class,"where e.orderId=?1 and e.status='HELD' order by e.seatId",o.id)){i.status="AVAILABLE";i.orderId=null;i.expiresAt=null;}
        db.first(VoucherUse.class,"where e.orderId=?1",o.id).ifPresent(use->{db.lock(Voucher.class,use.voucherId);if(use.status.equals("RESERVED"))use.status="RELEASED";});
    }
    public Payment preparePayment(Long userId,Long id){
        owned(userId,id);Order o=lockOrder(id);
        if(!o.status.equals("PENDING_PAYMENT")||!now().isBefore(o.expiresAt))throw new IllegalArgumentException("Đơn đã hết hạn.");
        if(o.total<1000)throw new IllegalArgumentException("MoMo yêu cầu tối thiểu 1.000đ. Hãy sửa ưu đãi trước khi thanh toán.");
        Optional<Payment> current=db.first(Payment.class,"where e.orderId=?1 and e.status in ('CREATED','PENDING','SUCCEEDED') order by e.id desc",id);
        if(current.isPresent())return current.get();
        Payment p=new Payment();p.orderId=id;p.requestId=UUID.randomUUID().toString();p.externalOrderId=o.code+"-"+UUID.randomUUID().toString().substring(0,8);p.amount=o.total;p.createdAt=now();db.save(p);o.frozen=true;return p;
    }
    public void paymentCreated(Long id,String payUrl){Payment p=db.lock(Payment.class,id);if(p.status.equals("CREATED")){p.status="PENDING";p.payUrl=payUrl;p.updatedAt=now();}}
    public void completeFree(Long userId,Long id){
        owned(userId,id);Order o=lockOrder(id);
        if(o.status.equals("PAID")&&o.total==0)return;
        editable(o);if(o.total!=0)throw new IllegalArgumentException("Đơn vẫn còn số tiền cần thanh toán.");
        Payment p=new Payment();p.provider="VOUCHER";p.orderId=id;p.requestId=UUID.randomUUID().toString();p.externalOrderId="FREE-"+o.code;p.amount=0;p.createdAt=now();db.save(p);o.frozen=true;
        paymentResult(p.externalOrderId,0,"voucher-"+p.id,0);
    }
    public void paymentResult(String externalOrder,long amount,String transaction,int code){
        Payment found=db.first(Payment.class,"where e.externalOrderId=?1",externalOrder).orElseThrow(()->new IllegalArgumentException("Không tìm thấy giao dịch."));
        Order o=lockOrder(found.orderId);Payment p=db.lock(Payment.class,found.id);
        if(amount!=p.amount||amount!=o.total)throw new IllegalArgumentException("Số tiền giao dịch không khớp.");
        if(p.status.equals("SUCCEEDED"))return;
        if(Set.of(1000,7000,7002,9000).contains(code)){p.status="PENDING";p.resultCode=code;p.updatedAt=now();return;}
        p.resultCode=code;p.updatedAt=now();
        if(code!=0){p.status="FAILED";return;}
        if(transaction==null||transaction.isBlank()||transaction.equals("0"))throw new IllegalArgumentException("Thiếu mã giao dịch MoMo.");
        p.transactionId=transaction;p.status="SUCCEEDED";
        if(!o.status.equals("PENDING_PAYMENT")||!now().isBefore(o.expiresAt)){
            if(o.status.equals("PENDING_PAYMENT"))release(o,"EXPIRED");p.reconciliation=true;return;
        }
        List<Inventory> seats=db.list(Inventory.class,"where e.orderId=?1 order by e.seatId",o.id);
        if(seats.size()!=db.list(OrderSeat.class,"where e.orderId=?1",o.id).size()||seats.stream().anyMatch(i->!i.status.equals("HELD"))){p.reconciliation=true;release(o,"EXPIRED");return;}
        issue(o,seats);
        db.first(VoucherUse.class,"where e.orderId=?1",o.id).ifPresent(use->{db.lock(Voucher.class,use.voucherId);use.status="USED";});
        Point points=new Point();points.userId=o.userId;points.orderId=o.id;points.points=o.total/1000;points.createdAt=now();db.save(points);
        User u=db.get(User.class,o.userId);notices.notify(u,"paid:"+o.id,"Đặt vé thành công · "+o.code,"Phim: "+o.movieTitle+"\nRạp: "+o.cinemaName+" · "+o.roomName+"\nSuất: "+db.date(o.showStartsAt)+"\nMở Vé của tôi trên CinePrime để xem QR và tải PDF.",true);
        db.audit(o.userId,"PAYMENT_CONFIRMED",o.code);
    }
    /** Sells the order's HELD inventory rows (locked by the caller) and issues one ticket per seat. */
    public void issue(Order o,List<Inventory> seats){
        for(Inventory inv:seats){inv.status="SOLD";inv.expiresAt=null;Ticket t=new Ticket();t.orderId=o.id;t.showtimeId=o.showtimeId;t.seatId=inv.seatId;t.code="T-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT);t.token=AccountService.token();db.save(t);}
        o.status="PAID";o.paidAt=now();
    }
}
