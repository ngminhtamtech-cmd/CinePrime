package vn.cineprime.admin;

import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cineprime.booking.BookingService;
import vn.cineprime.domain.Store;
import static vn.cineprime.domain.Domain.*;

@Service @Transactional
public class AdminService {
    public record Resource(String label,Class<? extends Row> type,String fields){}
    public static final Map<String,Resource> RESOURCES=new LinkedHashMap<>();
    static {
        add("movies","Phim",Movie.class,"title,slug,description,poster,banner,trailer,ageRating,status,duration,releaseDate,featured,active");
        add("genres","Thể loại",Genre.class,"name,active");add("people","Diễn viên / Đạo diễn",Person.class,"name,biography,active");
        add("movie-genres","Thể loại của phim",MovieGenre.class,"movieId,genreId");add("credits","Đoàn phim",Credit.class,"movieId,personId,kind");
        add("cinemas","Rạp",Cinema.class,"name,city,address,phone,description,active");add("rooms","Phòng",Room.class,"cinemaId,name,format,active");
        add("seats","Ghế / Bảo trì",Seat.class,"roomId,label,rowLabel,position,type,coupleGroup,active");
        add("showtimes","Lịch chiếu",Showtime.class,"movieId,roomId,startsAt,language,format,status,surcharge");
        add("prices","Bảng giá",Price.class,"cinemaId,seatType,dayType,fromMinute,toMinute,amount");
        add("products","Bắp nước / Combo",Product.class,"name,kind,image,description,price,active");
        add("vouchers","Voucher",Voucher.class,"code,description,kind,value,minimum,maximum,quota,perUser,startsAt,endsAt,active");
        add("contents","Nội dung / Banner / Chính sách",Content.class,"kind,title,slug,image,link,body,active,publishedAt");
        add("users","Người dùng",User.class,"email,name,phone,role,cinemaId,enabled");
        add("reviews","Duyệt bình luận",Review.class,"visible");
    }
    private static void add(String key,String label,Class<? extends Row> type,String fields){RESOURCES.put(key,new Resource(label,type,fields));}
    private final Store db;private final BookingService booking;private final PasswordEncoder passwords;private final Clock clock;
    public AdminService(Store db,BookingService booking,PasswordEncoder passwords,Clock clock){this.db=db;this.booking=booking;this.passwords=passwords;this.clock=clock;}
    public Resource resource(String key){Resource r=RESOURCES.get(key);if(r==null)throw new IllegalArgumentException("Mục quản lý không tồn tại.");return r;}
    public Row save(Long actor,String key,Long id,Map<String,String> values){
        Resource r=resource(key);Row row;
        try{row=id==null?r.type().getConstructor().newInstance():db.get(r.type(),id);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        if(row instanceof Review&&id==null)throw new IllegalArgumentException("Admin chỉ được duyệt bình luận có sẵn.");
        Long originalRoom=row instanceof Seat s?s.roomId:row instanceof Showtime s?s.roomId:null;
        if(originalRoom!=null)db.lock(Room.class,originalRoom);
        if(id!=null)db.lock(r.type(),id);
        boolean protectedShow=row instanceof Showtime s&&busyShow(s.id);
        String oldSchedule=row instanceof Showtime s?s.movieId+"|"+s.roomId+"|"+s.startsAt+"|"+s.status:"";
        if(row instanceof Seat seat&&id!=null&&busyRoom(seat.roomId))throw new IllegalArgumentException("Phòng có suất đang mở bán/giữ ghế/vé bán; không thể đổi sơ đồ.");
        if(row instanceof Room room&&id!=null&&busyRoom(room.id))throw new IllegalArgumentException("Phòng có lịch mở bán, không thể thay thông tin.");
        if(row instanceof Movie m&&id!=null&&!db.list(Showtime.class,"where e.movieId=?1 and e.status='OPEN'",id).isEmpty()&&Integer.parseInt(values.getOrDefault("duration","0"))!=m.duration)throw new IllegalArgumentException("Không đổi thời lượng phim đã có lịch mở bán.");
        for(String name:r.fields().split(","))try{
            Field f=r.type().getField(name);String v=values.getOrDefault(name,"").trim();
            if(v.length()>20000)throw new IllegalArgumentException("Nội dung quá dài.");
            Object parsed;
            if(f.getType()==boolean.class)parsed=v.equals("true");
            else if(f.getType()==int.class)parsed=Integer.parseInt(v);
            else if(f.getType()==long.class)parsed=Long.parseLong(v);
            else if(f.getType()==Long.class)parsed=v.isEmpty()?null:Long.valueOf(v);
            else if(f.getType()==Instant.class)parsed=v.isEmpty()?null:LocalDateTime.parse(v).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
            else if(f.getType()==LocalDate.class)parsed=v.isEmpty()?null:LocalDate.parse(v);
            else parsed=v;
            f.set(row,parsed);
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}catch(java.time.format.DateTimeParseException|NumberFormatException e){throw new IllegalArgumentException("Kiểm tra các trường số/ngày giờ.");}
        validate(row,values);
        if(row instanceof Showtime s){
            if(s.roomId!=null&&!s.roomId.equals(originalRoom))db.lock(Room.class,s.roomId);
            s.endsAt=s.startsAt.plusSeconds(db.get(Movie.class,s.movieId).duration*60L);
            if(protectedShow&&!oldSchedule.equals(s.movieId+"|"+s.roomId+"|"+s.startsAt+"|"+s.status))throw new IllegalArgumentException("Suất có lượt giữ hoặc vé đã bán. Không thể đổi/hủy.");
            if(!s.status.equals("CANCELLED"))for(Showtime other:db.list(Showtime.class,"where e.roomId=?1 and e.status<>'CANCELLED'",s.roomId))if(!Objects.equals(other.id,s.id)&&s.startsAt.isBefore(other.endsAt.plusSeconds(900))&&s.endsAt.plusSeconds(900).isAfter(other.startsAt))throw new IllegalArgumentException("Trùng lịch phòng, bao gồm 15 phút vệ sinh.");
            if(s.status.equals("OPEN")){
                if(!db.get(Room.class,s.roomId).active||!db.get(Cinema.class,db.get(Room.class,s.roomId).cinemaId).active||!db.get(Movie.class,s.movieId).active)throw new IllegalArgumentException("Phim/rạp/phòng đã ngừng hoạt động.");
                List<Seat> seats=db.list(Seat.class,"where e.roomId=?1 and e.active=true",s.roomId);if(seats.isEmpty())throw new IllegalArgumentException("Phòng chưa có ghế.");
                for(Seat seat:seats){booking.price(s,seat);if(seat.type.equals("COUPLE")&&db.list(Seat.class,"where e.roomId=?1 and e.coupleGroup=?2 and e.active=true",s.roomId,seat.coupleGroup).size()!=2)throw new IllegalArgumentException("Mỗi nhóm ghế đôi phải có đúng 2 ghế hoạt động.");}
            }
        }
        db.save(row);
        if(row instanceof Price p)for(Showtime s:db.list(Showtime.class,"where e.status='OPEN' and e.startsAt>?1",clock.instant()))if(db.get(Room.class,s.roomId).cinemaId.equals(p.cinemaId))for(Seat seat:db.list(Seat.class,"where e.roomId=?1 and e.active=true",s.roomId))booking.price(s,seat);
        if(row instanceof Showtime s&&s.status.equals("OPEN"))for(Seat seat:db.list(Seat.class,"where e.roomId=?1",s.roomId))if(db.first(Inventory.class,"where e.showtimeId=?1 and e.seatId=?2",s.id,seat.id).isEmpty()){Inventory i=new Inventory();i.showtimeId=s.id;i.seatId=seat.id;db.save(i);}
        db.audit(actor,"SAVE_"+key,row.id.toString());return row;
    }
    public boolean busyShow(Long id){return id!=null&&(!db.list(Order.class,"where e.showtimeId=?1 and (e.status='PAID' or (e.status='PENDING_PAYMENT' and e.expiresAt>?2))",id,clock.instant()).isEmpty());}
    public boolean busyRoom(Long id){return !db.list(Showtime.class,"where e.roomId=?1 and (e.status='OPEN' or e.endsAt>?2)",id,clock.instant()).isEmpty();}
    private void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
    private void choice(String value,String... allowed){require(Arrays.asList(allowed).contains(value),"Giá trị lựa chọn không hợp lệ.");}
    private void validate(Row row,Map<String,String> values){
        if(row instanceof Movie m){require(!m.title.isBlank()&&m.duration>0&&m.duration<=600,"Tên phim/thời lượng không hợp lệ.");require(m.slug.matches("[a-z0-9-]+"),"Slug chỉ gồm chữ thường, số và dấu gạch ngang.");choice(m.status,"COMING_SOON","NOW_SHOWING","STOPPED");choice(m.ageRating,"P","K","T13","T16","T18");media(m.poster);media(m.banner);media(m.trailer);}
        if(row instanceof Cinema c)require(!c.name.isBlank()&&!c.city.isBlank(),"Nhập tên rạp và thành phố.");
        if(row instanceof Room r){db.get(Cinema.class,r.cinemaId);require(!r.name.isBlank(),"Nhập tên phòng.");}
        if(row instanceof Seat s){db.lock(Room.class,s.roomId);require(!busyRoom(s.roomId),"Chỉ sửa sơ đồ trước khi tạo lịch mở bán.");require(!s.label.isBlank()&&!s.rowLabel.isBlank()&&s.position>0,"Thông tin ghế không hợp lệ.");choice(s.type,"STANDARD","VIP","COUPLE");if(s.type.equals("COUPLE"))require(s.coupleGroup!=null&&!s.coupleGroup.isBlank(),"Ghế đôi cần mã nhóm.");}
        if(row instanceof MovieGenre g){db.get(Movie.class,g.movieId);db.get(Genre.class,g.genreId);}
        if(row instanceof Credit c){db.get(Movie.class,c.movieId);db.get(Person.class,c.personId);choice(c.kind,"ACTOR","DIRECTOR");}
        if(row instanceof Genre g)require(!g.name.isBlank(),"Nhập thể loại.");
        if(row instanceof Person p)require(!p.name.isBlank(),"Nhập họ tên.");
        if(row instanceof Showtime s){db.get(Movie.class,s.movieId);db.get(Room.class,s.roomId);require(s.startsAt!=null&&s.surcharge>=0,"Giờ chiếu/phụ thu không hợp lệ.");choice(s.status,"DRAFT","OPEN","CANCELLED");}
        if(row instanceof Price p){db.lock(Cinema.class,p.cinemaId);choice(p.seatType,"STANDARD","VIP","COUPLE");choice(p.dayType,"WEEKDAY","WEEKEND");require(p.amount>0&&p.fromMinute>=0&&p.toMinute<=1440&&p.fromMinute<p.toMinute,"Giá hoặc khung phút không hợp lệ.");for(Price old:db.list(Price.class,"where e.cinemaId=?1 and e.seatType=?2 and e.dayType=?3",p.cinemaId,p.seatType,p.dayType))if(!Objects.equals(old.id,p.id)&&p.fromMinute<old.toMinute&&p.toMinute>old.fromMinute)throw new IllegalArgumentException("Trùng khung giá.");}
        if(row instanceof Product p){require(!p.name.isBlank()&&p.price>=0,"Tên/giá sản phẩm không hợp lệ.");choice(p.kind,"FOOD","DRINK","COMBO");media(p.image);}
        if(row instanceof Voucher v){v.code=v.code.toUpperCase(Locale.ROOT);choice(v.kind,"FIXED","PERCENT");require(v.code.matches("[A-Z0-9_-]{2,40}")&&v.value>0&&v.minimum>=0&&v.maximum>=0&&v.quota>0&&v.perUser>0&&v.startsAt!=null&&v.endsAt!=null&&v.startsAt.isBefore(v.endsAt),"Thông tin voucher không hợp lệ.");require(!v.kind.equals("PERCENT")||v.value<=100,"Phần trăm không vượt 100.");}
        if(row instanceof Content c){choice(c.kind,"BANNER","ARTICLE","OFFER","POLICY","ABOUT","CONTACT","NOTICE");require(!c.title.isBlank()&&c.slug.matches("[a-z0-9-]+"),"Tiêu đề/slug không hợp lệ.");media(c.image);require(c.link.isBlank()||c.link.startsWith("/")&&!c.link.startsWith("//"),"Liên kết nội dung phải nằm trong CinePrime.");}
        if(row instanceof User u){choice(u.role,"ADMIN","STAFF","CUSTOMER");u.email=u.email.toLowerCase(Locale.ROOT);require(u.email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")&&!u.name.isBlank(),"Thông tin người dùng không hợp lệ.");if(u.role.equals("STAFF"))db.get(Cinema.class,u.cinemaId);else u.cinemaId=null;
            // Lock all admins in ID order, preventing two concurrent demotions of the last administrators.
            List<User> admins=db.list(User.class,"where e.role='ADMIN' and e.enabled=true order by e.id");for(User a:admins)db.lock(User.class,a.id);
            if((!u.enabled||!u.role.equals("ADMIN"))&&admins.stream().noneMatch(a->!Objects.equals(a.id,u.id)))throw new IllegalArgumentException("Phải giữ ít nhất một Admin hoạt động.");
            if(u.id==null){String password=values.getOrDefault("initialPassword","");require(password.length()>=10&&password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=72,"Mật khẩu ban đầu cần 10 ký tự, tối đa 72 byte.");u.passwordHash=passwords.encode(password);}else u.authVersion++;
        }
    }
    private void media(String value){if(value!=null&&!value.isBlank())require(value.startsWith("/media/")||value.startsWith("/images/")||value.startsWith("https://www.youtube.com/embed/")||value.startsWith("https://www.youtube-nocookie.com/embed/"),"Dùng file đã upload hoặc URL nhúng YouTube hợp lệ.");}
    public void remove(Long actor,String key,Long id){
        Resource r=resource(key);Row row=db.get(r.type(),id);
        if(row instanceof Showtime s){db.lock(Room.class,s.roomId);db.lock(Showtime.class,s.id);require(!busyShow(id),"Suất có lượt giữ/vé bán, không thể hủy.");s.status="CANCELLED";}
        else if(row instanceof Seat s){db.lock(Room.class,s.roomId);require(!busyRoom(s.roomId),"Không đổi sơ đồ phòng đã có lịch.");s.active=false;}
        else if(row instanceof Price p){db.lock(Cinema.class,p.cinemaId);db.delete(p);db.em.flush();for(Showtime s:db.list(Showtime.class,"where e.status='OPEN' and e.startsAt>?1",clock.instant()))if(db.get(Room.class,s.roomId).cinemaId.equals(p.cinemaId))for(Seat seat:db.list(Seat.class,"where e.roomId=?1 and e.active=true",s.roomId))booking.price(s,seat);}
        else if(row instanceof User)throw new IllegalArgumentException("Dùng chức năng sửa để khóa tài khoản.");
        else if(row instanceof Review review)review.visible=false;
        else if(row instanceof MovieGenre||row instanceof Credit)db.delete(row);
        else try{r.type().getField("active").set(row,false);}catch(ReflectiveOperationException e){throw new IllegalArgumentException("Không xóa dữ liệu lịch sử. Hãy sửa cấu hình thay thế.");}
        db.audit(actor,"DISABLE_"+key,id.toString());
    }
    public void generateSeats(Long actor,Long roomId,int rows,int columns){
        db.lock(Room.class,roomId);require(!busyRoom(roomId)&&db.list(Seat.class,"where e.roomId=?1",roomId).isEmpty(),"Chỉ tạo sơ đồ cho phòng trống chưa có lịch.");require(rows>=1&&rows<=20&&columns>=2&&columns<=30,"Sơ đồ tối đa 20 hàng × 30 cột.");
        for(int r=0;r<rows;r++)for(int c=1;c<=columns;c++){Seat s=new Seat();s.roomId=roomId;s.rowLabel=String.valueOf((char)('A'+r));s.label=s.rowLabel+c;s.position=c;s.type=r>=rows/3&&r<rows-1?"VIP":"STANDARD";if(r==rows-1&&c<=columns/2*2){s.type="COUPLE";s.coupleGroup=s.rowLabel+"-"+((c+1)/2);}db.save(s);}db.audit(actor,"GENERATE_LAYOUT",roomId.toString());
    }
}
