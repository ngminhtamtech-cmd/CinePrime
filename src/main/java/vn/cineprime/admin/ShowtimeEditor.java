package vn.cineprime.admin;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import vn.cineprime.domain.Store;
import static vn.cineprime.domain.Domain.*;

/** Read-only editor context and advisory conflicts; AdminService rechecks under write locks. */
@Service @Transactional(readOnly=true)
public class ShowtimeEditor {
    private final Store db;private final Clock clock;private final AdminService admin;private final RoomSetup setup;
    public ShowtimeEditor(Store db,Clock clock,AdminService admin,RoomSetup setup){this.db=db;this.clock=clock;this.admin=admin;this.setup=setup;}
    public Map<String,String> values(Long id,Map<String,String> submitted,String returnTo){
        if(submitted!=null)return new HashMap<>(submitted);
        Map<String,String> v=new HashMap<>();Showtime s=id==null?new Showtime():db.get(Showtime.class,id);
        v.put("movieId",Objects.toString(s.movieId,""));v.put("roomId",Objects.toString(s.roomId,""));v.put("startsAt",s.startsAt==null?"":s.startsAt.atZone(ReportService.ZONE).toLocalDateTime().toString());v.put("status",s.status);v.put("language",s.language);v.put("format",s.format);v.put("surcharge",Long.toString(s.surcharge));
        if(id==null&&!returnTo.isBlank()){
            var params=UriComponentsBuilder.fromUriString(returnTo).build().getQueryParams();for(String name:List.of("cinemaId","movieId","roomId")){String value=params.getFirst(name);if(value!=null&&value.matches("[0-9]+"))v.put(name,value);}
            String day=params.getFirst("date");if(day!=null&&day.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))v.put("startsAt",day+"T10:00");
        }
        if(!v.get("roomId").isBlank())db.first(Room.class,"where e.id=?1",Long.valueOf(v.get("roomId"))).ifPresent(r->v.put("cinemaId",r.cinemaId.toString()));
        if(v.getOrDefault("cinemaId","").isBlank())db.first(Cinema.class,"where e.active=true order by e.name,e.id").ifPresent(c->v.put("cinemaId",c.id.toString()));
        return v;
    }
    public Map<String,Object> form(Long id,Map<String,String> values){
        String selected=values.getOrDefault("cinemaId","");Long cinema=selected.matches("[0-9]+")?Long.valueOf(selected):null;
        String start=values.getOrDefault("startsAt","");LocalDateTime time;try{time=LocalDateTime.parse(start);}catch(RuntimeException e){time=clock.instant().atZone(ReportService.ZONE).toLocalDate().atTime(10,0);}
        var model=new LinkedHashMap<String,Object>();model.put("cinemaId",selected);model.put("movieId",values.getOrDefault("movieId",""));model.put("roomId",values.getOrDefault("roomId",""));model.put("editorDate",time.toLocalDate().toString());model.put("editorTime",time.toLocalTime().toString());model.put("editorStartsAt",start);model.put("protectedShow",id!=null&&admin.busyShow(id));model.put("editorRooms",cinema==null?List.of():db.views(db.list(Room.class,"where e.cinemaId=?1 order by e.name,e.id",cinema)));model.put("editorMovies",db.views(db.list(Movie.class,"order by e.title,e.id")));return model;
    }
    private <T> jakarta.persistence.TypedQuery<T> query(String hql,Class<T> type,Map<String,Object> args){var q=db.entityManager().createQuery(hql,type);args.forEach(q::setParameter);return q;}
    public Map<String,Object> preview(Long cinemaId,Long roomId,LocalDate date,Long movieId,LocalDateTime startsAt,Long editingId,int requested){
        if(cinemaId==null||roomId==null||date==null)throw new IllegalArgumentException("Chọn rạp, phòng và ngày để xem lịch.");
        Room room=db.get(Room.class,roomId);if(!room.cinemaId.equals(cinemaId))throw new IllegalArgumentException("Phòng không thuộc rạp đang chọn.");
        Showtime editing=editingId==null?null:db.get(Showtime.class,editingId);boolean protectedShow=editing!=null&&admin.busyShow(editingId);
        Instant start=startsAt==null?null:startsAt.atZone(ReportService.ZONE).toInstant(),end=null;
        if(protectedShow){if(!editing.roomId.equals(roomId))throw new IllegalArgumentException("Suất đã có giao dịch, không thể đổi phòng.");start=editing.startsAt;end=editing.endsAt;}
        else if(start!=null&&movieId!=null)end=start.plusSeconds(db.get(Movie.class,movieId).duration*60L);
        Instant from=date.atStartOfDay(ReportService.ZONE).toInstant(),until=date.plusDays(1).atStartOfDay(ReportService.ZONE).toInstant();
        Map<String,Object> args=new HashMap<>(Map.of("room",roomId,"from",from.minusSeconds(900),"until",until));String base=" from "+db.entityName(Showtime.class)+" s, "+db.entityName(Movie.class)+" m where s.movieId=m.id and s.roomId=:room and s.status<>'CANCELLED'";
        if(editingId!=null){base+=" and s.id<>:editing";args.put("editing",editingId);}
        String conflict="false";long conflicts=0;
        if(start!=null&&end!=null){args.put("candidateStart",start.minusSeconds(900));args.put("candidateEnd",end.plusSeconds(900));conflict="(s.endsAt>:candidateStart and s.startsAt<:candidateEnd)";var conflictArgs=new HashMap<>(args);conflictArgs.remove("from");conflictArgs.remove("until");conflicts=query("select count(s)"+base+" and "+conflict,Long.class,conflictArgs).getSingleResult();}
        String fromQuery=base+" and ((s.startsAt<:until and s.endsAt>:from)"+(end==null?"":" or "+conflict)+")";
        long total=query("select count(s)"+fromQuery,Long.class,args).getSingleResult();int page=AdminPage.number(requested,total);
        String sort=end==null?"s.startsAt,s.id":"case when "+conflict+" then 0 else 1 end,s.startsAt,s.id";
        Instant candidateStart=start,candidateEnd=end;
        var records=query("select s,m.title"+fromQuery+" order by "+sort,Object[].class,args).setFirstResult(page*AdminPage.SIZE).setMaxResults(AdminPage.SIZE).getResultList();
        var rows=records.stream().map(row->{Showtime s=(Showtime)row[0];return Map.<String,Object>of("id",s.id,"movie",row[1],"when",db.date(s.startsAt),"interval",AdminReads.interval(s.startsAt,s.endsAt),"cleanupUntil",db.date(s.endsAt.plusSeconds(900)),"status",AdminPresentation.text(s.status),"conflict",candidateEnd!=null&&candidateStart.isBefore(s.endsAt.plusSeconds(900))&&candidateEnd.plusSeconds(900).isAfter(s.startsAt));}).toList();
        return Map.of("data",new AdminPage<>(rows,total,page,page>0,(page+1L)*AdminPage.SIZE<total),"conflictCount",conflicts,"proposal",end==null?Map.of():Map.of("end",db.date(end),"cleanupUntil",db.date(end.plusSeconds(900)),"interval",AdminReads.interval(start,end)),"readiness",setup.readiness(cinemaId,roomId,movieId,start,editingId),"serverTime",clock.instant().toString());
    }
}
