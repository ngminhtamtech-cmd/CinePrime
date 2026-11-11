package vn.cineprime.staff;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.cineprime.domain.Store;
import vn.cineprime.admin.AdminPresentation;
import vn.cineprime.admin.ReportService;
import static vn.cineprime.domain.Domain.*;

@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class StaffReadService {
    private final Store db;private final CheckInService checkin;private final Clock clock;
    public StaffReadService(Store db,CheckInService checkin,Clock clock){this.db=db;this.checkin=checkin;this.clock=clock;}
    public Map<String,Object> page(Long staffId,LocalDate day,Long selected){
        User staff=db.get(User.class,staffId);if(!staff.enabled||!staff.role.equals("STAFF"))throw new org.springframework.security.access.AccessDeniedException("Tài khoản không có quyền soát vé.");if(staff.cinemaId==null)return Map.of("unassigned",true,"staffCinema","Chưa phân công rạp","operationsRole","Soát vé");
        Instant now=clock.instant();if(day==null)day=now.atZone(ReportService.ZONE).toLocalDate();
        var shows=db.query("select s,m.title,r.name,c.name from %s s, %s m, %s r, %s c where s.movieId=m.id and s.roomId=r.id and r.cinemaId=c.id and c.id=?1 and s.startsAt>=?2 and s.startsAt<?3 and s.status='OPEN' order by s.startsAt,s.id".formatted(db.entityName(Showtime.class),db.entityName(Movie.class),db.entityName(Room.class),db.entityName(Cinema.class)),Object[].class,staff.cinemaId,day.atStartOfDay(ReportService.ZONE).toInstant(),day.plusDays(1).atStartOfDay(ReportService.ZONE).toInstant()).stream().map(row->show(row,now)).sorted(Comparator.comparingInt(v->(Integer)v.get("priority"))).toList();
        Map<String,Object> model=new LinkedHashMap<>();model.put("day",day.toString());model.put("shows",shows);model.put("staffCinema",db.get(Cinema.class,staff.cinemaId).name);model.put("operationsRole","Soát vé");model.put("selectedShow",selected);model.put("selected",Map.of());
        if(selected!=null){Showtime show=db.get(Showtime.class,selected);checkin.permitted(staffId,show);Map<String,Object> selectedView=shows.stream().filter(s->s.get("id").equals(selected)).findFirst().orElseThrow(()->new IllegalArgumentException("Suất không thuộc ngày đã chọn hoặc không còn mở vận hành. Hãy chọn lại suất."));model.put("selected",selectedView);model.put("ticketData",tickets(staffId,selected,"","",0));}
        return model;
    }
    private Map<String,Object> show(Object[] row,Instant now){Showtime s=(Showtime)row[0];boolean ready=!now.isBefore(s.startsAt.minusSeconds(3600))&&!now.isAfter(s.startsAt.plusSeconds(1800));return Map.of("id",s.id,"movieTitle",row[1],"roomName",row[2],"cinemaName",row[3],"when",db.date(s.startsAt),"window",db.date(s.startsAt.minusSeconds(3600))+" → "+db.date(s.startsAt.plusSeconds(1800)),"ready",ready,"state",ready?"Đang nhận khách":now.isBefore(s.startsAt)?"Sắp tới":"Đã hết giờ check-in","priority",ready?0:now.isBefore(s.startsAt)?1:2);}
    private String join(){return " from %s t, %s o, %s s, %s seat, %s r, %s c where t.orderId=o.id and t.showtimeId=s.id and t.seatId=seat.id and s.roomId=r.id and r.cinemaId=c.id".formatted(db.entityName(Ticket.class),db.entityName(Order.class),db.entityName(Showtime.class),db.entityName(Seat.class),db.entityName(Room.class),db.entityName(Cinema.class));}
    private Map<String,Object> ticket(Object[] row,Long selected,Instant now){Ticket t=(Ticket)row[0];Order order=(Order)row[1];Showtime s=(Showtime)row[2];var eligibility=CheckInService.eligibility(t,s,order,selected,now);Map<String,Object> view=new LinkedHashMap<>();view.put("id",t.id);view.put("code",t.code);view.put("orderCode",order.code);view.put("movieTitle",order.movieTitle);view.put("roomName",order.roomName);view.put("cinemaName",order.cinemaName);view.put("showWhen",db.date(order.showStartsAt));view.put("seat",row[3]);view.put("status",t.status);view.put("statusLabel",AdminPresentation.text(t.status));view.put("when",db.date(t.checkedInAt));view.put("canCheckIn",eligibility.allowed());view.put("reasonCode",eligibility.code());view.put("reason",eligibility.message());return Collections.unmodifiableMap(view);}
    public Map<String,Object> tickets(Long staffId,Long showtimeId,String q,String state,int page){
        Showtime show=db.get(Showtime.class,showtimeId);checkin.permitted(staffId,show);Instant now=clock.instant();Map<String,Long> counts=new HashMap<>();db.query("select t.status,count(t) from "+db.entityName(Ticket.class)+" t where t.showtimeId=?1 group by t.status",Object[].class,showtimeId).forEach(row->counts.put((String)row[0],(Long)row[1]));long used=counts.getOrDefault("USED",0L),valid=counts.getOrDefault("VALID",0L),total=used+valid;
        Map<String,Object> summary=Map.of("total",total,"used",used,"valid",valid,"rate",total==0?"—":String.format(Locale.ROOT,"%.1f%%",used*100.0/total));
        if(!state.isBlank()&&!Set.of("VALID","USED","VOID").contains(state))throw new IllegalArgumentException("Trạng thái vé không hợp lệ.");
        String filter=" and t.showtimeId=:show and (lower(t.code) like :q escape '!' or lower(o.code) like :q escape '!' or lower(seat.label) like :q escape '!')"+(state.isBlank()?"":" and t.status=:state");String search="%"+q.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        var count=db.entityManager().createQuery("select count(t)"+join()+filter,Long.class).setParameter("show",showtimeId).setParameter("q",search);var query=db.entityManager().createQuery("select t,o,s,seat.label"+join()+filter+" order by seat.rowLabel,seat.position,t.id",Object[].class).setParameter("show",showtimeId).setParameter("q",search);if(!state.isBlank()){count.setParameter("state",state);query.setParameter("state",state);}long matching=count.getSingleResult();int current=Math.max(0,page);int offset=Math.toIntExact(Math.min((long)current*25,Integer.MAX_VALUE));List<Map<String,Object>> tickets=query.setFirstResult(offset).setMaxResults(25).getResultList().stream().map(row->ticket(row,showtimeId,now)).toList();
        String showState=show.status.equals("CANCELLED")?"Suất đã hủy":now.isBefore(show.startsAt.minusSeconds(3600))?"Chưa tới giờ check-in":now.isAfter(show.startsAt.plusSeconds(1800))?"Đã hết giờ check-in":"Đang nhận khách";
        return Map.of("summary",summary,"tickets",tickets,"total",matching,"page",current,"hasMore",matching>(current+1L)*25,"serverTime",now.toString(),"updatedAt",db.date(now),"showState",showState);
    }
    public Map<String,Object> lookup(Long staffId,String code,Long selected){
        Showtime s=db.get(Showtime.class,selected);checkin.permitted(staffId,s);List<Long> ids;
        try{ids=checkin.lookup(staffId,code).stream().map(t->t.id).toList();}catch(IllegalArgumentException error){throw new CheckInService.StaffProblem("NOT_FOUND",error.getMessage());}
        Instant now=clock.instant();return Map.of("tickets",db.entityManager().createQuery("select t,o,s,seat.label"+join()+" and t.id in :ids order by t.id",Object[].class).setParameter("ids",ids).getResultList().stream().map(row->ticket(row,selected,now)).toList(),"serverTime",now.toString());
    }
    public Map<String,Object> one(Long staffId,Long id,Long selected){Showtime selectedShow=db.get(Showtime.class,selected);checkin.permitted(staffId,selectedShow);Ticket t=db.get(Ticket.class,id);checkin.permitted(staffId,db.get(Showtime.class,t.showtimeId));return ticket(db.entityManager().createQuery("select t,o,s,seat.label"+join()+" and t.id=:id",Object[].class).setParameter("id",id).getSingleResult(),selected,clock.instant());}
}
