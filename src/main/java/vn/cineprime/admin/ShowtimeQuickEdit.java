package vn.cineprime.admin;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cineprime.domain.Store;
import static vn.cineprime.domain.Domain.*;
import static vn.cineprime.admin.ShowtimeBatch.*;

/** Saves one stored showtime straight from the weekly calendar; every rule stays in AdminService. */
@Service @Transactional
public class ShowtimeQuickEdit {
    public record Change(String expected,String movieId,String roomId,String date,String time,String language,String format,String surcharge,String status) {}
    public record Item(Long id,String expected) {}
    public record Bulk(String action,List<Item> items) {}
    private final Store db;private final AdminService admin;private final Clock clock;
    public ShowtimeQuickEdit(Store db,AdminService admin,Clock clock){this.db=db;this.admin=admin;this.clock=clock;}
    /** Opens every chosen draft (action OPEN) or cancels every chosen show (action CANCEL) in one transaction: all or nothing.
        Rooms are locked first, then shows, both in id order; each show then goes through the same AdminService rules as a single edit. */
    public Map<String,Object> bulk(Long actor,Bulk command){
        if(command==null||command.items()==null||command.items().isEmpty())throw new Failure(422,"BULK_EMPTY","Chọn ít nhất một suất.",null);
        if(command.items().size()>200)throw new Failure(422,"BULK_TOO_LARGE","Mỗi lần thao tác tối đa 200 suất.",null);
        String action=text(command.action());if(!Set.of("OPEN","CANCEL").contains(action))throw new Failure(422,"BULK_ACTION","Thao tác không hợp lệ.",null);
        Map<Long,String> expected=new TreeMap<>();for(Item item:command.items()){if(item==null||item.id()==null)throw new Failure(422,"BULK_EMPTY","Thiếu mã suất.",null);if(expected.put(item.id(),text(item.expected()))!=null)throw new Failure(422,"BULK_DUPLICATE","Một suất được chọn hai lần.",null);}
        List<Showtime> current=new ArrayList<>();for(Long id:expected.keySet())current.add(db.get(Showtime.class,id));
        Set<Long> rooms=new TreeSet<>();Set<Long> cinemas=new TreeSet<>();for(Showtime s:current){rooms.add(s.roomId);cinemas.add(db.get(Room.class,s.roomId).cinemaId);}
        if(cinemas.size()>1)throw new Failure(422,"BULK_SCOPE","Chỉ thao tác hàng loạt trong một rạp.",null);
        for(Long room:rooms)db.lock(Room.class,room);
        List<Showtime> shows=new ArrayList<>();for(Long id:expected.keySet()){Showtime s=db.lock(Showtime.class,id);if(!token(s).equals(expected.get(id)))throw new Failure(409,"SHOWTIME_STALE","Có suất vừa được thay đổi ở nơi khác. Tải lại trang rồi chọn lại.",null);shows.add(s);}
        Instant now=clock.instant();
        for(Showtime s:shows){
            String name=db.get(Movie.class,s.movieId).title+" "+s.startsAt.atZone(ReportService.ZONE).toLocalTime().withSecond(0).withNano(0)+" "+db.get(Room.class,s.roomId).name;
            if(!s.startsAt.isAfter(now))throw new Failure(422,"BULK_REJECTED","Suất "+name+" đã bắt đầu. Bỏ chọn suất này rồi thử lại.",null);
            try{
                if(action.equals("OPEN")){
                    if(!"DRAFT".equals(s.status))throw new IllegalArgumentException("Chỉ mở bán được suất nháp.");
                    Map<String,String> values=new HashMap<>();values.put("movieId",s.movieId.toString());values.put("roomId",s.roomId.toString());values.put("startsAt",s.startsAt.atZone(ReportService.ZONE).toLocalDateTime().toString());
                    values.put("language",s.language);values.put("format",s.format);values.put("surcharge",Long.toString(s.surcharge));values.put("status","OPEN");values.put("cinemaId",cinemas.iterator().next().toString());
                    admin.save(actor,"showtimes",s.id,values);
                }else{if("CANCELLED".equals(s.status))throw new IllegalArgumentException("Suất đã huỷ.");admin.remove(actor,"showtimes",s.id);}
            }catch(IllegalArgumentException e){throw new Failure(422,"BULK_REJECTED","Suất "+name+": "+e.getMessage()+" Chưa thay đổi suất nào.",null);}
        }
        return Map.of("action",action,"count",shows.size(),"ids",List.copyOf(expected.keySet()));
    }
    /** The token the calendar hands back so a save never overwrites a show someone else just moved. */
    public static String token(Showtime s){return s.movieId+"|"+s.roomId+"|"+s.startsAt+"|"+s.status;}
    private Showtime claim(Long id,String expected,Long newRoom){
        Showtime current=db.get(Showtime.class,id);if("CANCELLED".equals(current.status))throw new Failure(409,"SHOWTIME_STALE","Suất đã bị hủy. Lịch đã được tải lại.",null);
        Set<Long> rooms=new TreeSet<>();rooms.add(current.roomId);if(newRoom!=null)rooms.add(newRoom);for(Long room:rooms)db.lock(Room.class,room);
        Showtime show=db.lock(Showtime.class,id);
        if(!token(show).equals(text(expected)))throw new Failure(409,"SHOWTIME_STALE","Suất vừa được thay đổi ở nơi khác. Lịch đã được tải lại, hãy kiểm tra rồi thao tác lại.",null);
        return show;
    }
    public Map<String,Object> update(Long actor,Long id,Change change){
        if(change==null)throw new IllegalArgumentException("Thiếu dữ liệu suất chiếu.");
        Long room=ShowtimeBatch.id(change.roomId()),movie=ShowtimeBatch.id(change.movieId());if(room==null)throw new AdminFormProblem("Chọn phòng.","roomId");if(movie==null)throw new AdminFormProblem("Chọn phim.","movieId");
        Showtime show=claim(id,change.expected(),room);
        LocalDateTime start;try{start=LocalDate.parse(text(change.date())).atTime(LocalTime.parse(text(change.time())));}catch(RuntimeException e){throw new AdminFormProblem("Nhập ngày và giờ bắt đầu hợp lệ.","startsAt");}
        // Same minute as stored keeps the stored instant exactly, seconds included, so untouched times never count as a change.
        LocalDateTime stored=show.startsAt.atZone(ReportService.ZONE).toLocalDateTime();if(stored.withSecond(0).withNano(0).equals(start))start=stored;
        // A show never moves to, or opens at, a start that has already passed (the calendar blocks it too; this is the server's own check).
        boolean opening="DRAFT".equals(show.status)&&"OPEN".equals(text(change.status()));
        if((!start.equals(stored)||opening)&&!start.atZone(ReportService.ZONE).toInstant().isAfter(clock.instant()))throw new AdminFormProblem("Giờ bắt đầu đã qua. Chỉ dời hoặc mở bán suất trong tương lai.","startsAt");
        Map<String,String> values=new HashMap<>();
        values.put("movieId",movie.toString());values.put("roomId",room.toString());values.put("startsAt",start.toString());
        values.put("language",text(change.language()).isBlank()?"Phụ đề":text(change.language()));values.put("format",text(change.format()).isBlank()?db.get(Room.class,room).format:text(change.format()));
        values.put("surcharge",text(change.surcharge()).isBlank()?"0":text(change.surcharge()));values.put("status",text(change.status()).isBlank()?show.status:text(change.status()));
        if(!Set.of("DRAFT","OPEN").contains(values.get("status")))throw new AdminFormProblem("Chọn Nháp hoặc Đang bán vé.","status");
        if(!values.get("surcharge").matches("[0-9]{1,12}"))throw new AdminFormProblem("Phụ thu phải là số nguyên VND không âm.","surcharge");
        values.put("cinemaId",db.get(Room.class,show.roomId).cinemaId.toString());
        if(!db.get(Room.class,room).cinemaId.toString().equals(values.get("cinemaId")))throw new AdminFormProblem("Chỉ dời suất sang phòng cùng rạp.","roomId");
        Showtime saved=(Showtime)admin.save(actor,"showtimes",id,values);
        return event(saved);
    }
    public Map<String,Object> cancel(Long actor,Long id,String expected){
        claim(id,expected,null);admin.remove(actor,"showtimes",id);
        return Map.of("id",id,"status","CANCELLED");
    }
    private Map<String,Object> event(Showtime s){
        Map<String,Object> e=new LinkedHashMap<>();e.put("id",s.id);e.put("movieId",s.movieId);e.put("roomId",s.roomId);e.put("movieTitle",db.get(Movie.class,s.movieId).title);
        e.put("startsAt",db.local(s.startsAt));e.put("endsAt",db.local(s.endsAt));e.put("cleanupUntil",db.local(s.endsAt.plusSeconds(ShowtimeRules.CLEANUP_SECONDS)));e.put("status",s.status);
        e.put("language",s.language);e.put("format",s.format);e.put("surcharge",s.surcharge);e.put("locked",admin.busyShow(s.id));e.put("token",token(s));return e;
    }
}
