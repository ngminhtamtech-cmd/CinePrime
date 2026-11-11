package vn.cineprime.admin;

import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cineprime.domain.Store;
import static vn.cineprime.domain.Domain.*;

/** Current operations are independent of the financial report's immutable snapshot. */
@Service @Transactional(readOnly=true)
public class OperationsService {
    private final Store db;private final Clock clock;private final byte[] cursorKey=new byte[32];
    public OperationsService(Store db,Clock clock){this.db=db;this.clock=clock;new SecureRandom().nextBytes(cursorKey);}
    private record Position(Instant anchor,long upper,Instant time,long id,int page){}
    public Map<String,Object> read(Long cinemaId){
        var reconciliation=page("reconciliation",cinemaId,"");var failures=page("failures",cinemaId,"");var latest=page("latest",cinemaId,"");var upcoming=page("upcoming",cinemaId,"");
        Map<String,Object> result=new LinkedHashMap<>();result.put("updatedAt",db.date(clock.instant()));result.put("reconciliationCount",reconciliation.get("total"));result.put("reconciliationAmount",reconciliation.get("totalAmount"));result.put("failedCount",failures.get("total"));result.put("newCount",latest.get("total"));
        var pages=Map.of("reconciliation",reconciliation,"failures",failures,"latest",latest,"upcoming",upcoming);result.put("pages",pages);pages.forEach((k,v)->result.put(k,v.get("rows")));return result;
    }
    public Map<String,Object> page(String section,Long cinemaId,String cursor){
        if(!Set.of("reconciliation","failures","latest","upcoming").contains(section))throw new IllegalArgumentException("Khối vận hành không hợp lệ.");
        boolean payment=section.equals("reconciliation")||section.equals("failures"),upcoming=section.equals("upcoming");
        String alias=payment?"p":upcoming?"s":"o";
        Position position;
        if(cursor.isBlank()){
            long upper=db.query("select coalesce(max(e.id),0) from "+db.entityName(payment?Payment.class:upcoming?Showtime.class:Order.class)+" e",Long.class).getFirst();
            position=new Position(clock.instant(),upper,null,0,0);
        }else position=decode(cursor,section,cinemaId);
        Instant now=position.anchor();String filter=cinemaId==null?"":" and r.cinemaId=:cinema";
        Map<String,Object> args=new HashMap<>(Map.of("upper",position.upper()));if(cinemaId!=null)args.put("cinema",cinemaId);
        String from,select,timeField,order;
        if(payment){
            from=" from "+db.entityName(Payment.class)+" p, "+db.entityName(Order.class)+" o, "+db.entityName(Showtime.class)+" s, "+db.entityName(Room.class)+" r where p.orderId=o.id and o.showtimeId=s.id and s.roomId=r.id";
            select="select p,o.code";timeField="coalesce(p.updatedAt,p.createdAt)";order="p.id desc";
            if(section.equals("reconciliation"))from+=" and p.reconciliation=true";
            else{from+=" and p.status='FAILED' and coalesce(p.updatedAt,p.createdAt)>=:since and coalesce(p.updatedAt,p.createdAt)<=:now";args.put("since",now.minusSeconds(86400));args.put("now",now);}
        }else if(upcoming){
            from=" from "+db.entityName(Showtime.class)+" s, "+db.entityName(Room.class)+" r, "+db.entityName(Movie.class)+" m, "+db.entityName(Cinema.class)+" c where s.roomId=r.id and s.movieId=m.id and r.cinemaId=c.id and s.status='OPEN' and s.startsAt>=:since and s.startsAt<=:until";
            args.put("since",now.minusSeconds(1800));args.put("until",now.plusSeconds(7200));select="select s,m.title,r.name,c.name";timeField="s.startsAt";order="s.startsAt,s.id";
        }else{
            from=" from "+db.entityName(Order.class)+" o, "+db.entityName(Showtime.class)+" s, "+db.entityName(Room.class)+" r, "+db.entityName(User.class)+" u where o.showtimeId=s.id and s.roomId=r.id and o.userId=u.id and o.createdAt>=:since and o.createdAt<=:now";
            args.put("since",now.atZone(ReportService.ZONE).toLocalDate().atStartOfDay(ReportService.ZONE).toInstant());args.put("now",now);select="select o,u.name";timeField="o.createdAt";order="o.createdAt desc,o.id desc";
        }
        from+=filter+" and "+alias+".id<=:upper";
        long total=query("select count(*)"+from,Long.class,args).getSingleResult();
        long amount=section.equals("reconciliation")?query("select coalesce(sum(p.amount),0)"+from,Long.class,args).getSingleResult():0;
        String seek="";
        if(position.page()>0){
            args.put("lastId",position.id());
            if(payment)seek=" and p.id<:lastId";
            else{args.put("lastTime",position.time());String compare=upcoming?">":"<";seek=" and ("+timeField+compare+":lastTime or ("+timeField+"=:lastTime and "+alias+".id"+compare+":lastId))";}
        }
        var records=query(select+from+seek+" order by "+order,Object[].class,args).setMaxResults(AdminPage.SIZE+1).getResultList();
        boolean more=records.size()>AdminPage.SIZE;if(more)records=records.subList(0,AdminPage.SIZE);
        List<Map<String,Object>> rows=new ArrayList<>();
        for(Object[] row:records){
            if(payment){Payment p=(Payment)row[0];rows.add(Map.of("id",p.orderId,"recordId",p.id,"label",row[1],"detail",section.equals("reconciliation")?AdminPresentation.text(p.status)+" · "+db.money(p.amount):"Lần thanh toán #"+p.id+" · "+db.date(p.updatedAt==null?p.createdAt:p.updatedAt)));}
            else if(upcoming){Showtime s=(Showtime)row[0];rows.add(Map.of("id",s.id,"recordId",s.id,"label",row[1],"detail",row[3]+" · "+row[2]+" · "+AdminReads.interval(s.startsAt,s.endsAt)+" · "+(now.isBefore(s.startsAt.minusSeconds(3600))?"Sắp tới":"Trong khung check-in")));}
            else{Order o=(Order)row[0];rows.add(Map.of("id",o.id,"recordId",o.id,"label",o.code+" · "+o.movieTitle,"detail",row[1]+" · "+(o.status.equals("PENDING_PAYMENT")?"Chưa thu tiền":o.status.equals("PAID")&&o.total==0?"Miễn phí hợp lệ":AdminPresentation.text(o.status))+" · "+(o.status.equals("PAID")?"":"Giá trị đơn: ")+db.money(o.total)));}
        }
        String next="";
        if(more){Object value=records.getLast()[0];long id=((Row)value).id;Instant time=value instanceof Showtime s?s.startsAt:value instanceof Order o?o.createdAt:now;next=encode(section,cinemaId,new Position(now,position.upper(),time,id,position.page()+1));}
        return Map.of("rows",List.copyOf(rows),"total",total,"totalAmount",db.money(amount),"page",position.page(),"hasPrevious",position.page()>0,"hasMore",more,"nextCursor",next,"updatedAt",db.date(now),"revision",Objects.hash(total,amount,rows));
    }
    private <T> jakarta.persistence.TypedQuery<T> query(String hql,Class<T> type,Map<String,Object> params){var q=db.entityManager().createQuery(hql,type);params.forEach(q::setParameter);return q;}
    private byte[] sign(String data){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(cursorKey,"HmacSHA256"));return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
    private String encode(String section,Long cinema,Position p){
        String data=String.join("|",section,Objects.toString(cinema,""),p.anchor().toString(),Long.toString(p.upper()),p.time().toString(),Long.toString(p.id()),Integer.toString(p.page()));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data.getBytes(StandardCharsets.UTF_8))+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(sign(data));
    }
    private Position decode(String cursor,String section,Long cinema){
        try{
            String[] parts=cursor.split("\\.",-1);if(parts.length!=2)throw new IllegalArgumentException();
            String data=new String(Base64.getUrlDecoder().decode(parts[0]),StandardCharsets.UTF_8);
            if(!MessageDigest.isEqual(sign(data),Base64.getUrlDecoder().decode(parts[1])))throw new IllegalArgumentException();
            String[] fields=data.split("\\|",-1);if(fields.length!=7||!fields[0].equals(section)||!fields[1].equals(Objects.toString(cinema,"")))throw new IllegalArgumentException();
            var p=new Position(Instant.parse(fields[2]),Long.parseLong(fields[3]),Instant.parse(fields[4]),Long.parseLong(fields[5]),Integer.parseInt(fields[6]));
            if(p.page()<1||p.id()<1||p.upper()<p.id()||p.anchor().isAfter(clock.instant())||!clock.instant().isBefore(p.anchor().plusSeconds(900)))throw new IllegalArgumentException();
            return p;
        }catch(RuntimeException e){throw new IllegalArgumentException("Trang vận hành đã hết hạn hoặc không hợp lệ. Bấm làm mới khối để xem dữ liệu hiện tại.");}
    }
}