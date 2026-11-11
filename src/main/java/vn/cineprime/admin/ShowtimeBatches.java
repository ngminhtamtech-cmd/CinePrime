package vn.cineprime.admin;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import vn.cineprime.domain.Store;
import static vn.cineprime.domain.Domain.*;
import static vn.cineprime.admin.ShowtimeBatch.*;

@Service
public class ShowtimeBatches {
    private final Store db;private final Clock clock;private final JdbcTemplate jdbc;private final TransactionTemplate tx;
    private final ObjectMapper json=new ObjectMapper();
    public ShowtimeBatches(Store db,Clock clock,JdbcTemplate jdbc,PlatformTransactionManager manager){this.db=db;this.clock=clock;this.jdbc=jdbc;tx=new TransactionTemplate(manager);}
    private String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private void structure(Input input){
        if(input==null||input.cinemaId()==null||!Set.of("MANUAL","EXCEL","MIXED").contains(Objects.toString(input.source(),""))||!Set.of("DRAFT","OPEN").contains(Objects.toString(input.targetStatus(),""))||input.rows()==null||input.rows().isEmpty()||input.rows().size()>LIMIT)throw new Failure(422,"INVALID_BATCH","Chọn rạp, nguồn nhập, trạng thái và từ 1 đến 500 suất.",null);
        Set<String> keys=new HashSet<>();for(Line line:input.rows())if(line==null||text(line.rowKey()).isEmpty()||line.rowKey().length()>80||!keys.add(line.rowKey()))throw new Failure(422,"INVALID_ROWS","Mỗi dòng cần mã duy nhất.",null);
    }
    @Transactional(readOnly=true) public Preview preview(Input input){return inspect(input);}
    private Preview inspect(Input input){
        structure(input);Cinema cinema=db.get(Cinema.class,input.cinemaId());
        Map<Long,Room> rooms=new LinkedHashMap<>();db.list(Room.class,"where e.cinemaId=?1 order by e.id",cinema.id).forEach(r->rooms.put(r.id,r));
        Set<Long> selected=new TreeSet<>();input.rows().forEach(r->{Long id=id(r.movieId());if(id!=null)selected.add(id);});
        Map<Long,Movie> movies=new LinkedHashMap<>();if(!selected.isEmpty())db.entityManager().createQuery("select m from "+db.entityName(Movie.class)+" m where m.id in :ids order by m.id",Movie.class).setParameter("ids",selected).getResultList().forEach(m->movies.put(m.id,m));
        Set<Long> visible=new HashSet<>(db.query("select distinct x.movieId from "+db.entityName(MovieGenre.class)+" x, "+db.entityName(Genre.class)+" g where x.genreId=g.id and g.active=true",Long.class));
        List<Seat> seats=db.query("select s from "+db.entityName(Seat.class)+" s, "+db.entityName(Room.class)+" r where s.roomId=r.id and r.cinemaId=?1 order by s.id",Seat.class,cinema.id);
        List<Price> prices=db.list(Price.class,"where e.cinemaId=?1 order by e.id",cinema.id);
        List<Checked> rows=new ArrayList<>();List<Showtime> proposals=new ArrayList<>();Instant now=clock.instant();
        for(Line raw:input.rows()){
            List<Problem> errors=new ArrayList<>();List<String> warnings=new ArrayList<>();Movie movie=movies.get(id(raw.movieId()));Room room=rooms.get(id(raw.roomId()));
            if(movie==null)errors.add(new Problem("MOVIE_NOT_FOUND","movieId","Mã phim không tồn tại."));
            if(room==null)errors.add(new Problem("ROOM_NOT_IN_CINEMA","roomId","Mã phòng không thuộc rạp đang chọn."));
            LocalDate day=null;LocalTime time=null;
            try{day=LocalDate.parse(text(raw.date()));if(day.getYear()<1000||day.getYear()>9999)throw new IllegalArgumentException();}catch(RuntimeException e){day=null;errors.add(new Problem("INVALID_DATE","date","Ngày chiếu không hợp lệ (năm từ 1000 đến 9999)."));}
            try{time=LocalTime.parse(text(raw.time()));if(time.getSecond()!=0||time.getNano()!=0)throw new IllegalArgumentException();}catch(RuntimeException e){time=null;errors.add(new Problem("INVALID_TIME","time","Nhập giờ HH:mm, từ 00:00 đến 23:59."));}
            long surcharge=0;try{surcharge=text(raw.surcharge()).isBlank()?0:Long.parseLong(text(raw.surcharge()));if(surcharge<0)throw new IllegalArgumentException();}catch(RuntimeException e){errors.add(new Problem("INVALID_SURCHARGE","surcharge","Phụ thu phải là số nguyên VND không âm."));}
            String language=text(raw.language()).isBlank()?"Phụ đề":text(raw.language()),format=text(raw.format()).isBlank()?(room==null?"":room.format):text(raw.format());
            if(language.length()>255)errors.add(new Problem("TOO_LONG","language","Ngôn ngữ tối đa 255 ký tự."));if(format.length()>255)errors.add(new Problem("TOO_LONG","format","Định dạng tối đa 255 ký tự."));
            if(language.equals("[Công thức không được hỗ trợ]")||language.equals("[Giá trị ô không hợp lệ]"))errors.add(new Problem("INVALID_CELL","language","Ô ngôn ngữ phải là văn bản, không dùng công thức."));
            if(format.equals("[Công thức không được hỗ trợ]")||format.equals("[Giá trị ô không hợp lệ]"))errors.add(new Problem("INVALID_CELL","format","Ô định dạng phải là văn bản, không dùng công thức."));
            Showtime proposed=null;String start="",end="",cleanup="";
            if(movie!=null&&room!=null&&day!=null&&time!=null){
                proposed=new Showtime();proposed.movieId=movie.id;proposed.roomId=room.id;proposed.startsAt=day.atTime(time).atZone(ReportService.ZONE).toInstant();proposed.endsAt=proposed.startsAt.plusSeconds(movie.duration*60L);proposed.language=language;proposed.format=format;proposed.surcharge=surcharge;proposed.status=input.targetStatus();
                start=db.local(proposed.startsAt);end=db.local(proposed.endsAt);cleanup=db.local(proposed.endsAt.plusSeconds(900));
                if(!proposed.startsAt.isAfter(now))errors.add(new Problem("START_IN_PAST","time","Giờ bắt đầu phải sau giờ hiện tại của máy chủ."));
                var opening=ShowtimeRules.openingErrors(movie,room,cinema,seats.stream().filter(s->s.roomId.equals(room.id)).toList(),prices,visible.contains(movie.id),proposed.startsAt,surcharge);
                if("OPEN".equals(input.targetStatus()))opening.forEach(s->errors.add(new Problem("CANNOT_OPEN","status",s)));else warnings.addAll(opening);
            }
            Line normalized=new Line(raw.rowKey(),raw.sourceRow(),text(raw.movieId()),text(raw.roomId()),day==null?text(raw.date()):day.toString(),time==null?text(raw.time()):time.toString(),language,format,text(raw.surcharge()).isEmpty()?"0":text(raw.surcharge()));
            rows.add(new Checked(normalized,movie==null?text(raw.movieId()):movie.title,room==null?text(raw.roomId()):room.name,start,end,cleanup,errors,warnings));proposals.add(proposed);
        }
        Set<LocalDate> weeks=new HashSet<>();
        for(Checked row:rows)try{weeks.add(LocalDate.parse(row.input().date()).with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));}catch(RuntimeException ignored){}
        if(weeks.size()>1)for(Checked row:rows)row.errors().add(new Problem("MULTIPLE_WEEKS","date","Mỗi đợt chỉ xếp một tuần Thứ Hai–Chủ nhật. Chuyển các suất về cùng tuần trước khi lưu."));
        List<Showtime> valid=proposals.stream().filter(Objects::nonNull).toList();List<Showtime> existing=List.of();Map<Long,String> existingTitles=new HashMap<>();
        if(!valid.isEmpty()){
            Instant from=valid.stream().map(s->s.startsAt).min(Instant::compareTo).orElseThrow().minusSeconds(900),until=valid.stream().map(s->s.endsAt).max(Instant::compareTo).orElseThrow().plusSeconds(900);
            var occupied=db.query("select s,m.title from "+db.entityName(Showtime.class)+" s, "+db.entityName(Room.class)+" r, "+db.entityName(Movie.class)+" m where s.roomId=r.id and s.movieId=m.id and r.cinemaId=?1 and s.status<>'CANCELLED' and s.startsAt<?2 and s.endsAt>?3 order by s.id",Object[].class,cinema.id,until,from);
            existing=occupied.stream().map(v->{Showtime s=(Showtime)v[0];existingTitles.put(s.id,(String)v[1]);return s;}).toList();
            for(int i=0;i<proposals.size();i++){Showtime a=proposals.get(i);if(a==null)continue;Checked row=rows.get(i);
                for(Showtime b:existing)if(a.roomId.equals(b.roomId)&&ShowtimeRules.overlaps(a.startsAt,a.endsAt,b.startsAt,b.endsAt))row.errors().add(new Problem("EXISTING_CONFLICT","time",rooms.get(a.roomId).name+" đang có "+existingTitles.get(b.id)+" ("+db.local(b.startsAt)+" – "+db.local(b.endsAt)+"), gồm 15 phút vệ sinh."));
                for(int j=i+1;j<proposals.size();j++){Showtime b=proposals.get(j);if(b!=null&&a.roomId.equals(b.roomId)&&ShowtimeRules.overlaps(a.startsAt,a.endsAt,b.startsAt,b.endsAt)){
                    row.errors().add(new Problem("BATCH_CONFLICT","time","Trùng "+rows.get(j).movieTitle()+" tại "+rows.get(j).roomName()+" ("+db.local(b.startsAt)+"), gồm 15 phút vệ sinh.",List.of(rows.get(j).input().rowKey())));
                    rows.get(j).errors().add(new Problem("BATCH_CONFLICT","time","Trùng "+row.movieTitle()+" tại "+row.roomName()+" ("+db.local(a.startsAt)+"), gồm 15 phút vệ sinh.",List.of(row.input().rowKey())));
                }}
            }
        }
        int invalid=(int)rows.stream().filter(r->!r.errors().isEmpty()).count();
        // Fingerprint intentionally excludes the clock and includes all authoritative inputs affecting the preview.
        String fingerprint=hash(List.of(input.cinemaId(),input.source(),input.targetStatus(),rows,movies.values(),rooms.values(),cinema,seats,prices,visible.stream().sorted().toList(),existing));
        return new Preview(rows,rows.size(),invalid,invalid==0,fingerprint,now.toString());
    }
    private record Stored(String hash,Result result){}
    private Optional<Stored> stored(Long actor,String key){return jdbc.query("SELECT payload_hash,result_json FROM showtime_batch_results WHERE actor_id=? AND request_key=?",(rs,n)->new Stored(rs.getString(1),json.readValue(rs.getString(2),Result.class)),actor,key).stream().findFirst();}
    private Result replay(Stored stored,String payloadHash){if(!stored.hash().equals(payloadHash))throw new Failure(409,"IDEMPOTENCY_KEY_REUSED","Mã lần lưu đã dùng cho dữ liệu khác.",null);return stored.result();}
    public Optional<Result> result(Long actor,String key){return stored(actor,key).map(Stored::result);}
    public Result commit(Long actor,Commit command){
        if(command==null)throw new IllegalArgumentException("Thiếu dữ liệu xác nhận.");structure(command.input());
        try{UUID.fromString(command.requestKey());}catch(RuntimeException e){throw new IllegalArgumentException("Mã lần lưu không hợp lệ.");}
        String payloadHash=hash(command.input());var old=stored(actor,command.requestKey());if(old.isPresent())return replay(old.get(),payloadHash);
        try{return tx.execute(status->{
            Input input=command.input();Set<Long> roomIds=new TreeSet<>(),movieIds=new TreeSet<>();for(Line line:input.rows()){Long room=id(line.roomId()),movie=id(line.movieId());if(room!=null)roomIds.add(room);if(movie!=null)movieIds.add(movie);}
            for(Long room:roomIds)db.lock(Room.class,room);for(Long movie:movieIds)db.lockRead(Movie.class,movie);db.lock(Cinema.class,input.cinemaId());
            var concurrent=stored(actor,command.requestKey());if(concurrent.isPresent())return replay(concurrent.get(),payloadHash);
            Preview preview=inspect(input);if(!preview.canCommit())throw new Failure(422,"BATCH_INVALID","Sửa các dòng lỗi trước khi tạo suất.",preview);
            if(!preview.fingerprint().equals(command.fingerprint()))throw new Failure(409,"PREVIEW_STALE","Lịch hoặc dữ liệu đã thay đổi. Kiểm tra lại bản xem trước rồi xác nhận.",preview);
            jdbc.update("INSERT INTO showtime_batch_results(actor_id,cinema_id,request_key,payload_hash,result_json,committed_at) VALUES (?,?,?,?,?,?)",actor,input.cinemaId(),command.requestKey(),payloadHash,"{}",java.sql.Timestamp.from(clock.instant()));
            List<Created> created=new ArrayList<>();for(Checked checked:preview.rows()){
                Line line=checked.input();Showtime s=new Showtime();s.movieId=id(line.movieId());s.roomId=id(line.roomId());s.startsAt=LocalDateTime.parse(checked.startsAt()).atZone(ReportService.ZONE).toInstant();s.endsAt=LocalDateTime.parse(checked.endsAt()).atZone(ReportService.ZONE).toInstant();s.language=line.language();s.format=line.format();s.surcharge=Long.parseLong(line.surcharge());s.status=input.targetStatus();db.save(s);if("OPEN".equals(s.status))ShowtimeRules.inventory(db,s);created.add(new Created(line.rowKey(),s.id,line.date()));
            }
            Result result=new Result(command.requestKey(),input.cinemaId(),input.targetStatus(),created,clock.instant().toString());
            jdbc.update("UPDATE showtime_batch_results SET result_json=? WHERE actor_id=? AND request_key=?",json.writeValueAsString(result),actor,command.requestKey());db.audit(actor,"CREATE_SHOWTIME_BATCH",command.requestKey()+" · "+created.size()+" suất");return result;
        });}catch(DataIntegrityViolationException e){var concurrent=stored(actor,command.requestKey());if(concurrent.isPresent())return replay(concurrent.get(),payloadHash);throw e;}
    }
    @Transactional(readOnly=true) public Map<String,Object> calendar(Long cinemaId,LocalDate day){
        return calendarRange(cinemaId,null,day,day.plusDays(1));
    }
    @Transactional(readOnly=true) public Map<String,Object> week(Long cinemaId,Long roomId,LocalDate weekStart){
        if(weekStart==null||weekStart.getDayOfWeek()!=DayOfWeek.MONDAY||weekStart.getYear()<1000||weekStart.getYear()>9998)throw new IllegalArgumentException("Ngày bắt đầu tuần phải là Thứ Hai, năm từ 1000 đến 9998.");
        if(roomId!=null&&!db.get(Room.class,roomId).cinemaId.equals(cinemaId))throw new IllegalArgumentException("Chọn phòng thuộc rạp đang quản lý.");
        return calendarRange(cinemaId,roomId,weekStart,weekStart.plusDays(7));
    }
    private Map<String,Object> calendarRange(Long cinemaId,Long roomId,LocalDate startDay,LocalDate endDay){
        Cinema cinema=db.get(Cinema.class,cinemaId);Instant from=startDay.atStartOfDay(ReportService.ZONE).toInstant(),until=endDay.atStartOfDay(ReportService.ZONE).toInstant();
        var rooms=db.list(Room.class,"where e.cinemaId=?1 order by e.name,e.id",cinemaId).stream().map(r->Map.of("id",r.id,"name",r.name,"format",r.format,"active",r.active)).toList();
        String hql="select s,m.title from "+db.entityName(Showtime.class)+" s, "+db.entityName(Room.class)+" r, "+db.entityName(Movie.class)+" m where s.roomId=r.id and s.movieId=m.id and r.cinemaId=?1 and s.status<>'CANCELLED' and s.startsAt<?2 and s.endsAt>?3"+(roomId==null?"":" and s.roomId=?4")+" order by s.startsAt,s.id";
        var records=roomId==null?db.query(hql,Object[].class,cinemaId,until.plusSeconds(36900),from.minusSeconds(900)):db.query(hql,Object[].class,cinemaId,until.plusSeconds(36900),from.minusSeconds(900),roomId);
        var events=records.stream().map(v->{Showtime s=(Showtime)v[0];return Map.of("id",s.id,"movieId",s.movieId,"roomId",s.roomId,"movieTitle",v[1],"startsAt",db.local(s.startsAt),"endsAt",db.local(s.endsAt),"cleanupUntil",db.local(s.endsAt.plusSeconds(900)),"status",s.status);}).toList();
        var movies=db.list(Movie.class,"order by e.title,e.id").stream().map(m->Map.of("id",(Object)m.id,"title",m.title,"poster",Objects.toString(m.poster,""),"duration",m.duration,"active",m.active,"status",vn.cineprime.domain.MovieLifecycle.status(m,vn.cineprime.domain.MovieLifecycle.today(clock)),"ageRating",Objects.toString(m.ageRating,"P"))).toList();
        return Map.of("cinemaId",cinemaId,"cinemaName",cinema.name,"date",startDay.toString(),"weekStart",startDay.toString(),"rangeEnd",endDay.toString(),"rooms",rooms,"movies",movies,"events",events,"serverTime",clock.instant().toString());
    }
}
