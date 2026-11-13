package vn.cineprime.admin;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cineprime.domain.Store;
import static vn.cineprime.domain.Domain.*;

/** Cinema-wide prices viewed from a room. Layout versions never version money. */
@Service @Transactional(readOnly=true)
public class RoomPrices {
    private final Store db;private final AdminService admin;
    public RoomPrices(Store db,AdminService admin){this.db=db;this.admin=admin;}
    public static String version(Price p){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest((p.id+"|"+p.cinemaId+"|"+p.seatType+"|"+p.dayType+"|"+p.fromMinute+"|"+p.toMinute+"|"+p.amount).getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private Map<String,Object> row(Price p){return Map.of("id",p.id,"seatType",p.seatType,"dayType",p.dayType,"fromMinute",AdminPresentation.time(p.fromMinute),"toMinute",AdminPresentation.time(p.toMinute),"amount",Long.toString(p.amount),"amountText",db.cash(p.amount),"version",version(p));}
    public Map<String,Object> page(Long roomId,String type,String day,int requested){
        Room room=db.get(Room.class,roomId);String where=" where p.cinemaId=:cinema";Map<String,Object> args=new HashMap<>(Map.of("cinema",room.cinemaId));
        if(!type.isBlank()){where+=" and p.seatType=:type";args.put("type",type);}if(!day.isBlank()){where+=" and p.dayType=:day";args.put("day",day);}
        String from=" from "+db.entityName(Price.class)+" p"+where;var count=db.entityManager().createQuery("select count(p)"+from,Long.class);args.forEach(count::setParameter);long total=count.getSingleResult();int page=AdminPage.number(requested,total);
        var query=db.entityManager().createQuery("select p"+from+" order by p.dayType,p.seatType,p.fromMinute,p.id",Price.class);args.forEach(query::setParameter);
        var data=new LinkedHashMap<>(new AdminPage<>(query.setFirstResult(page*5).setMaxResults(5).getResultList().stream().map(this::row).toList(),total,page,page>0,(page+1L)*5<total).model());data.put("cinemaId",room.cinemaId);return data;
    }
    @Transactional public Map<String,Object> save(Long actor,Long roomId,Long priceId,Map<String,String> input){
        Room room=db.lockRead(Room.class,roomId);db.lock(Cinema.class,room.cinemaId);
        if(priceId!=null){Price price=db.lock(Price.class,priceId);if(!price.cinemaId.equals(room.cinemaId))throw new IllegalArgumentException("Giá không thuộc rạp của phòng này.");if(!version(price).equals(input.get("priceVersion")))throw new org.springframework.dao.OptimisticLockingFailureException("Giá đã thay đổi. Đọc lại trước khi lưu.");}
        Map<String,String> fields=new HashMap<>();for(String key:List.of("seatType","dayType","fromMinute","toMinute","amount","priceVersion"))if(input.containsKey(key))fields.put(key,input.get(key));fields.put("cinemaId",room.cinemaId.toString());
        Price saved=(Price)admin.save(actor,"prices",priceId,AdminPresentation.normalize(fields));return Map.of("message","Đã lưu giá của rạp.","row",row(saved));
    }
}
