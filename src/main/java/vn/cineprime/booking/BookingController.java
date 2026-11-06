package vn.cineprime.booking;

import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.cineprime.auth.SecurityConfig.SessionUser;
import vn.cineprime.auth.SecurityConfig.RateLimit;
import vn.cineprime.domain.Store;
import vn.cineprime.web.Catalog;
import vn.cineprime.payment.MomoService;
import vn.cineprime.media.TicketFiles;
import static vn.cineprime.domain.Domain.*;

@Controller
public class BookingController {
    private final Store db;private final BookingService bookings;private final Catalog catalog;private final MomoService momo;private final TicketFiles files;private final RateLimit rates;
    public BookingController(Store db,BookingService bookings,Catalog catalog,MomoService momo,TicketFiles files,RateLimit rates){this.db=db;this.bookings=bookings;this.catalog=catalog;this.momo=momo;this.files=files;this.rates=rates;}
    @GetMapping("/booking/{id}") public String seats(@PathVariable Long id,Model model){model.addAttribute("show",catalog.show(db.get(Showtime.class,id)));return "seats";}
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    @GetMapping("/api/showtimes/{id}/seats") @ResponseBody public Map<String,Object> seats(@PathVariable Long id){Showtime show=db.get(Showtime.class,id);Map<String,Long> prices=new HashMap<>();List<Map<String,Object>> list=db.query("select i,s from %s i, %s s where i.seatId=s.id and i.showtimeId=?1 order by i.seatId".formatted(db.entityName(Inventory.class),db.entityName(Seat.class)),Object[].class,id).stream().map(row->{Inventory i=(Inventory)row[0];Seat seat=(Seat)row[1];Map<String,Object> v=db.view(seat);String state=!seat.active?"MAINTENANCE":i.status.equals("HELD")&&!bookings.now().isBefore(i.expiresAt)?"AVAILABLE":i.status;v.put("state",state);long price=seat.active?prices.computeIfAbsent(seat.type,type->bookings.price(show,seat)):0;v.put("price",price);v.put("priceText",seat.active?db.money(price):"Bảo trì");return v;}).toList();return Map.of("seats",list,"serverTime",bookings.now().toString());}
    public record HoldRequest(Long showtimeId,List<Long> seats){}
    @PostMapping("/api/holds") @ResponseBody public Map<String,Object> hold(@AuthenticationPrincipal SessionUser user,@RequestBody HoldRequest request){if(!rates.allow("hold:"+user.id(),10,60))throw new IllegalArgumentException("Thử giữ ghế quá nhiều lần. Vui lòng đợi một phút.");Order o=bookings.hold(user.id(),request.showtimeId(),request.seats());return Map.of("url","/orders/"+o.id,"expiresAt",o.expiresAt.toString());}
    @GetMapping("/orders/{id}") public String order(@AuthenticationPrincipal SessionUser u,@PathVariable Long id,Model model){Order o=bookings.owned(u.id(),id);if(o.status.equals("PENDING_PAYMENT")&&!bookings.now().isBefore(o.expiresAt)){bookings.expire(id);o=bookings.owned(u.id(),id);}model.addAttribute("order",catalog.order(o));model.addAttribute("seatLines",db.list(OrderSeat.class,"where e.orderId=?1",id).stream().map(l->{Map<String,Object> v=db.view(l);v.put("priceText",db.money(l.price));return v;}).toList());model.addAttribute("productLines",db.list(OrderProduct.class,"where e.orderId=?1",id).stream().map(l->{Map<String,Object> v=db.view(l);v.put("priceText",db.money(l.price*l.quantity));return v;}).toList());model.addAttribute("products",db.list(Product.class,"where e.active=true").stream().map(p->{Map<String,Object> v=db.view(p);v.put("priceText",db.money(p.price));v.put("quantity",db.first(OrderProduct.class,"where e.orderId=?1 and e.productId=?2",id,p.id).map(l->l.quantity).orElse(0));return v;}).toList());model.addAttribute("voucherCode",o.voucherId==null?"":db.get(Voucher.class,o.voucherId).code);model.addAttribute("tickets",db.list(Ticket.class,"where e.orderId=?1",id).stream().map(catalog::ticket).toList());model.addAttribute("payments",db.views(db.list(Payment.class,"where e.orderId=?1 order by e.id desc",id)));model.addAttribute("canVerify",!db.list(Payment.class,"where e.orderId=?1 and e.provider='MOMO'",id).isEmpty());model.addAttribute("momoConfigured",momo.configured());return "order";}
    @PostMapping("/orders/{id}/cart") public String cart(@AuthenticationPrincipal SessionUser u,@PathVariable Long id,@RequestParam Map<String,String> params){Map<Long,Integer> quantities=new HashMap<>();for(var e:params.entrySet())if(e.getKey().startsWith("product_"))quantities.put(Long.valueOf(e.getKey().substring(8)),Integer.valueOf(e.getValue()));bookings.updateCart(u.id(),id,quantities,params.get("voucher"));return "redirect:/orders/"+id;}
    @PostMapping("/orders/{id}/cancel") public String cancel(@AuthenticationPrincipal SessionUser u,@PathVariable Long id){bookings.cancel(u.id(),id);return "redirect:/orders/"+id;}
    @PostMapping("/orders/{id}/pay") public String pay(@AuthenticationPrincipal SessionUser u,@PathVariable Long id){Order order=bookings.owned(u.id(),id);if(order.total==0){bookings.completeFree(u.id(),id);return "redirect:/orders/"+id;}return "redirect:"+momo.create(u.id(),id);}
    @PostMapping("/orders/{id}/verify") public String verify(@AuthenticationPrincipal SessionUser u,@PathVariable Long id){bookings.owned(u.id(),id);Payment p=db.first(Payment.class,"where e.orderId=?1 order by e.id desc",id).orElseThrow(()->new IllegalArgumentException("Đơn chưa có yêu cầu thanh toán."));momo.query(p.id);return "redirect:/orders/"+id;}
    @GetMapping("/api/orders/{id}") @ResponseBody public Map<String,Object> status(@AuthenticationPrincipal SessionUser u,@PathVariable Long id){Order o=bookings.owned(u.id(),id);if(o.status.equals("PENDING_PAYMENT")&&!bookings.now().isBefore(o.expiresAt)){bookings.expire(id);o=bookings.owned(u.id(),id);}return Map.of("status",o.status,"expiresAt",o.expiresAt.toString(),"serverTime",bookings.now().toString());}
    @PostMapping("/payments/momo/ipn") public ResponseEntity<Void> ipn(@RequestBody Map<String,Object> payload){momo.ipn(payload);return ResponseEntity.noContent().build();}
    private Ticket ownTicket(SessionUser u,Long id){Ticket ticket=db.get(Ticket.class,id);bookings.owned(u.id(),ticket.orderId);return ticket;}
    @GetMapping("/tickets/{id}/qr.png") public ResponseEntity<byte[]> qr(@AuthenticationPrincipal SessionUser u,@PathVariable Long id)throws Exception{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).body(files.qr(ownTicket(u,id)));}
    @GetMapping("/tickets/{id}/download") public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal SessionUser u,@PathVariable Long id)throws Exception{Ticket ticket=ownTicket(u,id);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename="+ticket.code+".pdf").contentType(MediaType.APPLICATION_PDF).body(files.pdf(ticket));}
}
