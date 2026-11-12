package vn.cineprime.admin;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cineprime.domain.Store;
import vn.cineprime.web.Catalog;
import static vn.cineprime.domain.Domain.*;

/** Database-filtered management reads. Never changes booking, payment or pricing state. */
@Service @Transactional(readOnly=true)
public class AdminReads {
    private final Store db; private final Catalog catalog; private final Clock clock;
    public AdminReads(Store db,Catalog catalog,Clock clock){this.db=db;this.catalog=catalog;this.clock=clock;}
    private String entity(Class<? extends Row> type){return db.entityName(type);}
    private <T> TypedQuery<T> query(String hql,Class<T> type,Map<String,Object> args){var q=db.entityManager().createQuery(hql,type);args.forEach(q::setParameter);return q;}
    private String search(String q){return "%"+q.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";}
    private AdminPage<Object[]> page(String select,String from,String order,Map<String,Object> args,int requested){
        long total=query("select count(*)"+from,Long.class,args).getSingleResult();int page=AdminPage.number(requested,total);
        var rows=query(select+from+order,Object[].class,args).setFirstResult(page*AdminPage.SIZE).setMaxResults(AdminPage.SIZE).getResultList();
        return new AdminPage<>(rows,total,page,page>0,(page+1L)*AdminPage.SIZE<total);
    }
    private Map<String,Object> mapped(AdminPage<Object[]> p,List<Map<String,Object>> rows){return new AdminPage<>(rows,p.total(),p.page(),p.hasPrevious(),p.hasMore()).model();}
    public Map<String,Object> orderSection(Long id,String section,int requested){
        Class<? extends Row> type=switch(section){case "lines"->OrderSeat.class;case "combos"->OrderProduct.class;case "payments"->Payment.class;case "tickets"->Ticket.class;default->throw new IllegalArgumentException("Phần đơn không hợp lệ.");};
        long total=db.count(type,"where e.orderId=?1",id);int page=AdminPage.number(requested,total);
        var records=query("select e from "+entity(type)+" e where e.orderId=:id order by e.id desc",type,Map.of("id",id)).setFirstResult(page*AdminPage.SIZE).setMaxResults(AdminPage.SIZE).getResultList();
        Map<Long,String> seats=new HashMap<>();if(section.equals("tickets")&&!records.isEmpty())db.query("select s.id,s.label from "+entity(Seat.class)+" s where s.id in ?1",Object[].class,records.stream().map(r->((Ticket)r).seatId).toList()).forEach(r->seats.put((Long)r[0],(String)r[1]));
        List<Map<String,Object>> rows=records.stream().map(row->{
            if(row instanceof OrderSeat l)return Map.<String,Object>of("seat",l.label,"type",AdminPresentation.text(l.seatType),"price",db.money(l.price));
            if(row instanceof OrderProduct l)return Map.<String,Object>of("name",l.name,"quantity",l.quantity,"price",db.money(Math.multiplyExact(l.price,l.quantity)));
            var v=db.view(row);if(row instanceof Payment p){v.put("statusLabel",AdminPresentation.text(p.status));v.put("amountText",db.money(p.amount));}if(row instanceof Ticket t){v.put("seat",seats.getOrDefault(t.seatId,"—"));v.put("statusLabel",AdminPresentation.text(t.status));v.put("when",db.date(t.checkedInAt));}return v;
        }).toList();return new AdminPage<>(rows,total,page,page>0,(page+1L)*AdminPage.SIZE<total).model();
    }
    public static String interval(Instant start,Instant end){
        var a=start.atZone(ReportService.ZONE);var b=end.atZone(ReportService.ZONE);var f=DateTimeFormatter.ofPattern("HH:mm");
        long days=java.time.temporal.ChronoUnit.DAYS.between(a.toLocalDate(),b.toLocalDate());
        return f.format(a)+"–"+f.format(b)+(days>0?" (+"+days+" ngày)":"");
    }
    public List<Map<String,Object>> movies(Long cinema,LocalDate day){
        if(cinema==null)return List.of();
        return query("select distinct m.id,m.title from "+entity(Showtime.class)+" s, "+entity(Movie.class)+" m, "+entity(Room.class)+" r where s.movieId=m.id and s.roomId=r.id and r.cinemaId=:cinema and s.startsAt>=:from and s.startsAt<:until order by m.title,m.id",Object[].class,Map.of("cinema",cinema,"from",day.atStartOfDay(ReportService.ZONE).toInstant(),"until",day.plusDays(1).atStartOfDay(ReportService.ZONE).toInstant())).getResultList().stream().map(r->Map.<String,Object>of("id",r[0],"name",r[1])).toList();
    }
    public Map<String,Object> schedule(LocalDate day,Long cinema,Long movie,String q,String state,int requested){
        return schedule(day,cinema,null,movie,q,state,requested);
    }
    public Map<String,Object> schedule(LocalDate day,Long cinema,Long room,Long movie,String q,String state,int requested){
        String from=" from "+entity(Showtime.class)+" s, "+entity(Movie.class)+" m, "+entity(Room.class)+" r, "+entity(Cinema.class)+" c where s.movieId=m.id and s.roomId=r.id and r.cinemaId=c.id and s.startsAt>=:from and s.startsAt<:until";
        Map<String,Object> args=new HashMap<>(Map.of("from",day.atStartOfDay(ReportService.ZONE).toInstant(),"until",day.plusDays(1).atStartOfDay(ReportService.ZONE).toInstant()));
        if(cinema==null)from+=" and 1=0";else{from+=" and c.id=:cinema";args.put("cinema",cinema);}
        if(room!=null){from+=" and r.id=:room";args.put("room",room);}
        if(movie!=null){from+=" and m.id=:movie";args.put("movie",movie);}if(!state.isBlank()){from+=" and s.status=:state";args.put("state",state);}
        if(!q.isBlank()){from+=" and (lower(m.title) like :q escape '!' or lower(r.name) like :q escape '!')";args.put("q",search(q));}
        var p=page("select s,m.title,r.name,c.name",from," order by m.title,s.startsAt,s.id",args,requested);
        return mapped(p,p.rows().stream().map(row->{var s=(Showtime)row[0];return Map.<String,Object>of("id",s.id,"movieTitle",row[1],"roomName",row[2],"cinemaName",row[3],"when",interval(s.startsAt,s.endsAt),"status",AdminPresentation.text(s.status));}).toList());
    }
    public Map<String,Object> orders(String q,String state,String paymentState,Long cinema,LocalDate day,int requested){
        String from=" from "+entity(Order.class)+" o left join "+entity(User.class)+" u on u.id=o.userId, "+entity(Showtime.class)+" s, "+entity(Room.class)+" r where o.showtimeId=s.id and s.roomId=r.id";
        Map<String,Object> args=new HashMap<>();
        if(!q.isBlank()){from+=" and (lower(o.code) like :q escape '!' or lower(o.movieTitle) like :q escape '!' or lower(u.email) like :q escape '!' or lower(u.name) like :q escape '!')";args.put("q",search(q));}
        if(!state.isBlank()){from+=" and o.status=:state";args.put("state",state);}if(cinema!=null){from+=" and r.cinemaId=:cinema";args.put("cinema",cinema);}
        if(day!=null){from+=" and o.createdAt>=:from and o.createdAt<:until";args.put("from",day.atStartOfDay(ReportService.ZONE).toInstant());args.put("until",day.plusDays(1).atStartOfDay(ReportService.ZONE).toInstant());}
        if(!paymentState.isBlank()){from+=" and exists (select p.id from "+entity(Payment.class)+" p where p.orderId=o.id and "+(paymentState.equals("RECONCILIATION")?"p.reconciliation=true)":"p.status=:payment)");if(!paymentState.equals("RECONCILIATION"))args.put("payment",paymentState);}
        var p=page("select o,coalesce(u.email,''),coalesce(u.name,'Khách vãng lai')",from," order by o.createdAt desc,o.id desc",args,requested);
        Map<Long,List<Payment>> payments=new HashMap<>();if(!p.rows().isEmpty())db.list(Payment.class,"where e.orderId in ?1 order by e.id desc",p.rows().stream().map(r->((Order)r[0]).id).toList()).forEach(a->payments.computeIfAbsent(a.orderId,n->new ArrayList<>()).add(a));
        return mapped(p,p.rows().stream().map(row->{Order o=(Order)row[0];var v=catalog.order(o);v.put("email",row[1]);v.put("customerName",row[2]);v.put("channelLabel",o.channel.equals("COUNTER")?"Quầy":"Online");v.put("created",db.date(o.createdAt));v.put("statusLabel",AdminPresentation.text(o.status));var ps=payments.getOrDefault(o.id,List.of());v.put("paymentLabel",ps.isEmpty()?"Chưa tạo thanh toán":AdminPresentation.text(ps.getFirst().status)+(ps.stream().anyMatch(a->a.reconciliation)?" · Cần đối soát":""));return v;}).toList());
    }
    /** Counter cash shifts; expected cash of open shifts is read live, closed shifts use their snapshot. */
    public Map<String,Object> shifts(Long cinema,LocalDate day,String state,int requested){
        String from=" from "+entity(Shift.class)+" s, "+entity(User.class)+" u, "+entity(Cinema.class)+" c where s.staffId=u.id and s.cinemaId=c.id";Map<String,Object> args=new HashMap<>();
        if(cinema!=null){from+=" and s.cinemaId=:cinema";args.put("cinema",cinema);}if(!state.isBlank()){from+=" and s.status=:state";args.put("state",state);}
        if(day!=null){from+=" and s.openedAt>=:from and s.openedAt<:until";args.put("from",day.atStartOfDay(ReportService.ZONE).toInstant());args.put("until",day.plusDays(1).atStartOfDay(ReportService.ZONE).toInstant());}
        var p=page("select s,u.name,c.name",from," order by s.openedAt desc,s.id desc",args,requested);
        return mapped(p,p.rows().stream().map(row->{Shift s=(Shift)row[0];long cashIn=query("select coalesce(sum(p.amount),0) from "+entity(Payment.class)+" p where p.shiftId=:id and p.provider='CASH' and p.status='SUCCEEDED'",Long.class,Map.of("id",s.id)).getSingleResult();long expected=s.expectedCash!=null?s.expectedCash:s.openingCash+cashIn;
            Map<String,Object> v=new LinkedHashMap<>();v.put("id",s.id);v.put("staff",row[1]);v.put("cinema",row[2]);v.put("opened",db.date(s.openedAt));v.put("closed",s.closedAt==null?"Đang mở":db.date(s.closedAt));v.put("status",AdminPresentation.text(s.status));v.put("opening",db.money(s.openingCash));v.put("cashIn",db.money(expected-s.openingCash));v.put("expected",db.money(expected));v.put("counted",s.countedCash==null?"—":db.money(s.countedCash));
            v.put("difference",s.countedCash==null?"—":(s.countedCash>expected?"+":"")+db.money(s.countedCash-expected));v.put("mismatch",s.countedCash!=null&&s.countedCash!=expected);v.put("orders",db.count(Order.class,"where e.shiftId=?1 and e.status='PAID'",s.id));v.put("cancelled",db.count(Order.class,"where e.shiftId=?1 and e.cancelledAt is not null",s.id));v.put("note",Objects.toString(s.note,""));return v;}).toList());
    }
    public Map<String,Object> audit(String q,int requested){
        String from=" from "+entity(Audit.class)+" a left join "+entity(User.class)+" u on u.id=a.actorId where 1=1";Map<String,Object> args=new HashMap<>();
        if(!q.isBlank()){from+=" and (lower(coalesce(u.name,'Hệ thống')) like :q escape '!' or lower(a.action) like :q escape '!' or lower(coalesce(a.target,'')) like :q escape '!')";args.put("q",search(q));}
        var p=page("select a,coalesce(u.name,'Hệ thống')",from," order by a.createdAt desc,a.id desc",args,requested);
        return mapped(p,p.rows().stream().map(row->{var a=(Audit)row[0];return Map.<String,Object>of("when",db.date(a.createdAt),"actor",row[1],"action",a.action,"target",Objects.toString(a.target,"—"));}).toList());
    }
    public Map<String,Object> voucherUsage(Long id,String q,String state,int requested){
        String from=" from "+entity(VoucherUse.class)+" v, "+entity(Order.class)+" o, "+entity(User.class)+" u where v.orderId=o.id and v.userId=u.id and v.voucherId=:id";Map<String,Object> args=new HashMap<>(Map.of("id",id));
        if(!q.isBlank()){from+=" and (lower(o.code) like :q escape '!' or lower(u.email) like :q escape '!' or lower(u.name) like :q escape '!')";args.put("q",search(q));}if(!state.isBlank()){from+=" and v.status=:state";args.put("state",state);}
        var p=page("select v,o.code,u.email",from," order by v.id desc",args,requested);
        Map<String,Object> model=new HashMap<>(mapped(p,p.rows().stream().map(row->{var v=(VoucherUse)row[0];return Map.<String,Object>of("orderId",v.orderId,"orderCode",row[1],"email",row[2],"status",AdminPresentation.text(v.status),"when",db.date(v.expiresAt));}).toList()));
        model.put("used",db.count(VoucherUse.class,"where e.voucherId=?1 and e.status='USED'",id));model.put("reserved",db.count(VoucherUse.class,"where e.voucherId=?1 and e.status='RESERVED' and e.expiresAt>?2",id,clock.instant()));return model;
    }
}
