package vn.cineprime.web;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cineprime.domain.Store;
import vn.cineprime.media.MediaService;
import static vn.cineprime.domain.Domain.*;

/** Home hero slides: admin-managed banners in their order, falling back to featured / now-showing movies when none is live. */
@Service @Transactional(readOnly=true)
public class BannerService {
    public static final String INTERVAL="banner.interval";public static final int MIN_SECONDS=5,MAX_SECONDS=10,DEFAULT_SECONDS=6;
    static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");static final DateTimeFormatter WHEN=DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private final Store db;private final Catalog catalog;private final MediaService media;private final Clock clock;
    public BannerService(Store db,Catalog catalog,MediaService media,Clock clock){this.db=db;this.catalog=catalog;this.media=media;this.clock=clock;}

    public int interval(){return db.first(Setting.class,"where e.name=?1",INTERVAL).map(s->{try{return clamp(Integer.parseInt(s.value.trim()));}catch(NumberFormatException e){return DEFAULT_SECONDS;}}).orElse(DEFAULT_SECONDS);}
    static int clamp(int seconds){return Math.max(MIN_SECONDS,Math.min(MAX_SECONDS,seconds));}
    @Transactional public int interval(Long actor,int seconds){int value=clamp(seconds);Setting s=db.first(Setting.class,"where e.name=?1",INTERVAL).orElseGet(()->{Setting n=new Setting();n.name=INTERVAL;return n;});s.value=String.valueOf(value);db.save(s);db.audit(actor,"SAVE_BANNER_INTERVAL",String.valueOf(value));return value;}

    boolean live(Banner b,Instant now){return b.active&&(b.startsAt==null||!b.startsAt.isAfter(now))&&(b.endsAt==null||b.endsAt.isAfter(now));}

    public List<Map<String,Object>> slides(){
        Instant now=clock.instant();List<Banner> banners=db.list(Banner.class,"where e.active=true order by e.sortOrder,e.id").stream().filter(b->live(b,now)).toList();
        Map<Long,Map<String,Object>> movies=new HashMap<>();if(banners.stream().anyMatch(b->b.movieId!=null)||banners.isEmpty())catalog.movies(null,null,null,null,null).forEach(m->movies.put(((Number)m.get("id")).longValue(),m));
        List<Map<String,Object>> slides=new ArrayList<>();
        for(Banner b:banners){
            if("MOVIE".equals(b.kind)){var m=movies.get(b.movieId);if(m==null||Boolean.TRUE.equals(m.get("ended")))continue;var s=movieSlide(m,b.image);if(s!=null)slides.add(s);}
            else{String image=media.bannerOrEmpty(b.image);if(image.isBlank())continue;String mode=media.bannerMode(image);slides.add(slide("PROMO",b.title,Objects.toString(b.link,""),image,mode.isEmpty()?"framed":mode));}
        }
        if(!slides.isEmpty())return slides;
        // Nothing scheduled: featured movies, otherwise the newest now-showing titles, that have a usable landscape image.
        var listed=movies.values().stream().filter(m->!Boolean.TRUE.equals(m.get("ended"))).sorted((a,b)->Long.compare(((Number)b.get("id")).longValue(),((Number)a.get("id")).longValue())).toList();
        var featured=listed.stream().filter(m->Boolean.TRUE.equals(m.get("featured"))).map(m->movieSlide(m,null)).filter(Objects::nonNull).toList();
        return featured.isEmpty()?listed.stream().filter(m->"NOW_SHOWING".equals(m.get("status"))).map(m->movieSlide(m,null)).filter(Objects::nonNull).limit(5).toList():featured;
    }
    /** Image-only slide: the banner artwork carries the title. Movies without a usable landscape image stay off the hero (null). */
    Map<String,Object> movieSlide(Map<String,Object> m,String own){
        String image=own!=null&&!own.isBlank()?media.bannerOrEmpty(own):"";if(image.isBlank()||media.bannerMode(image).isEmpty())image=Objects.toString(m.get("banner"),"");String mode=media.bannerMode(image);
        return mode.isEmpty()?null:slide("MOVIE",Objects.toString(m.get("title"),""),"/movies/"+m.get("slug"),image,mode);
    }
    static Map<String,Object> slide(String kind,String title,String href,String image,String mode){Map<String,Object> s=new HashMap<>();s.put("kind",kind);s.put("title",title);s.put("href",href);s.put("image",image);s.put("mode",mode);return s;}

    /** Admin list in display order, with the reason a row is or is not on the home page right now. */
    public List<Map<String,Object>> rows(){
        Instant now=clock.instant();List<Banner> banners=db.list(Banner.class,"order by e.sortOrder,e.id");Map<Long,Movie> movies=new HashMap<>();
        for(Banner b:banners)if(b.movieId!=null)movies.computeIfAbsent(b.movieId,id->db.get(Movie.class,id));
        List<Map<String,Object>> rows=new ArrayList<>();
        for(int i=0;i<banners.size();i++){
            Banner b=banners.get(i);Movie m=b.movieId==null?null:movies.get(b.movieId);Map<String,Object> v=db.view(b);
            String image=media.bannerOrEmpty(b.image);if(image.isBlank()&&m!=null)image=media.bannerOrEmpty(m.banner);
            v.put("thumb",image.isBlank()?(m==null?"":media.imageOrPlaceholder(m.poster)):image);v.put("sharp",media.wide(image));int[] size=media.size(image);String mode=media.bannerMode(image);
            boolean missing=m!=null&&mode.isEmpty();
            v.put("display","wide".equals(mode)?"":"framed".equals(mode)?"Ảnh "+size[0]+"×"+size[1]+"px · hiển thị giữa khung trên nền tối (dùng 1920×640 để phủ kín)":missing?"Phim chưa có ảnh ngang (rộng từ "+MediaService.FRAMED_MIN_WIDTH+"px) · chưa lên banner. Bấm Sửa để tải ảnh.":"Ảnh nhỏ · hiển thị đúng kích thước, không phủ kín");
            v.put("target",m!=null?"Phim · "+m.title:b.link==null||b.link.isBlank()?"Không có liên kết":b.link);
            v.put("window",(b.startsAt==null?"Ngay":b.startsAt.atZone(ZONE).format(WHEN))+" → "+(b.endsAt==null?"Không hạn":b.endsAt.atZone(ZONE).format(WHEN)));
            String state;String tone;
            if(!b.active){state="Đang tắt";tone="muted";}
            else if(b.startsAt!=null&&b.startsAt.isAfter(now)){state="Chờ tới lịch";tone="info";}
            else if(b.endsAt!=null&&!b.endsAt.isAfter(now)){state="Hết hạn";tone="muted";}
            else if(m!=null&&(!m.active||"ENDED".equals(vn.cineprime.domain.MovieLifecycle.status(m,vn.cineprime.domain.MovieLifecycle.today(clock))))){state="Phim đã ngừng";tone="warn";}
            else if(missing){state="Thiếu ảnh ngang";tone="warn";}
            else{state="Đang hiển thị";tone="ok";}
            v.put("state",state);v.put("tone",tone);v.put("first",i==0);v.put("last",i==banners.size()-1);v.put("kindLabel","MOVIE".equals(b.kind)?"Banner phim":"Khuyến mãi");rows.add(v);
        }
        return rows;
    }
    public Map<String,Object> form(Long id){
        Map<String,Object> v=new HashMap<>();Banner b=id==null?new Banner():db.get(Banner.class,id);
        v.put("id",b.id);v.put("kind",b.kind);v.put("movieId",b.movieId==null?"":b.movieId.toString());v.put("title",Objects.toString(b.title,""));v.put("image",Objects.toString(b.image,""));v.put("link",Objects.toString(b.link,""));v.put("active",b.active);
        v.put("startsAt",b.startsAt==null?"":b.startsAt.atZone(ZONE).toLocalDateTime().toString());v.put("endsAt",b.endsAt==null?"":b.endsAt.atZone(ZONE).toLocalDateTime().toString());return v;
    }

    @Transactional public Banner save(Long actor,Map<String,String> in){
        String id=in.getOrDefault("id","").trim();Banner b=id.isEmpty()?new Banner():db.lock(Banner.class,Long.valueOf(id));
        String kind=in.getOrDefault("kind","").trim();if(!Set.of("MOVIE","PROMO").contains(kind))throw new IllegalArgumentException("Chọn loại banner: Banner phim hoặc Khuyến mãi.");b.kind=kind;
        String title=in.getOrDefault("title","").trim();if(title.length()>255)throw new IllegalArgumentException("Tiêu đề tối đa 255 ký tự.");
        String image=media.image(in.getOrDefault("image",""));if(!image.isBlank()&&!media.framed(image))throw new IllegalArgumentException("Ảnh banner cần là ảnh ngang (tỉ lệ từ 4:3), rộng tối thiểu "+MediaService.FRAMED_MIN_WIDTH+"px; khuyến nghị 1920×640. Hãy tải ảnh khác.");b.image=image.isBlank()?null:image;
        if(kind.equals("MOVIE")){
            String movie=in.getOrDefault("movieId","").trim();if(!movie.matches("[1-9][0-9]*"))throw new IllegalArgumentException("Chọn phim cho banner phim.");
            Movie m=db.get(Movie.class,Long.valueOf(movie));if(!m.active)throw new IllegalArgumentException("Phim đang ẩn khỏi website, không thể đưa lên banner.");
            b.movieId=m.id;b.link=null;b.title=title.isEmpty()?m.title:title;
        }else{
            if(b.image==null)throw new IllegalArgumentException("Banner khuyến mãi cần ảnh. Chọn ảnh ngang từ máy và đợi tải xong.");
            if(title.isEmpty())throw new IllegalArgumentException("Nhập tiêu đề (dùng làm mô tả ảnh cho người dùng trình đọc màn hình).");
            String link=in.getOrDefault("link","").trim();if(!link.isEmpty()&&(!link.startsWith("/")||link.startsWith("//")||link.length()>255))throw new IllegalArgumentException("Liên kết phải là đường dẫn trong CinePrime, ví dụ /offers hoặc /content/uu-dai-thang-11.");
            b.movieId=null;b.link=link.isEmpty()?null:link;b.title=title;
        }
        b.startsAt=time(in.get("startsAt"));b.endsAt=time(in.get("endsAt"));if(b.startsAt!=null&&b.endsAt!=null&&!b.endsAt.isAfter(b.startsAt))throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu.");
        b.active="true".equals(in.get("active"));
        if(b.id==null)b.sortOrder=nextOrder();
        db.save(b);db.audit(actor,"SAVE_BANNER",String.valueOf(b.id));return b;
    }
    static Instant time(String value){String v=Objects.toString(value,"").trim();if(v.isEmpty())return null;try{return LocalDateTime.parse(v).atZone(ZONE).toInstant();}catch(java.time.format.DateTimeParseException e){throw new IllegalArgumentException("Thời gian không hợp lệ.");}}

    /** A movie put on the website gets one MOVIE banner (appended, enabled) the first time it is saved; admins then reorder, disable or delete it. */
    @Transactional public void ensureMovie(Long actor,Movie m){
        if(m.id==null||!m.active||m.endDate!=null&&m.endDate.isBefore(vn.cineprime.domain.MovieLifecycle.today(clock)))return;
        if(db.count(Banner.class,"where e.movieId=?1",m.id)>0)return;
        Banner b=new Banner();b.kind="MOVIE";b.movieId=m.id;b.title=m.title.length()>255?m.title.substring(0,255):m.title;b.active=true;b.sortOrder=nextOrder();db.save(b);db.audit(actor,"AUTO_BANNER",String.valueOf(b.id));
    }
    int nextOrder(){return db.list(Banner.class,"order by e.sortOrder desc").stream().findFirst().map(x->x.sortOrder+1).orElse(1);}
    @Transactional public void move(Long actor,Long id,int delta){
        List<Banner> all=new ArrayList<>(db.list(Banner.class,"order by e.sortOrder,e.id"));int at=-1;for(int i=0;i<all.size();i++)if(all.get(i).id.equals(id))at=i;
        if(at<0)throw new IllegalArgumentException("Không tìm thấy banner.");int to=at+delta;if(to<0||to>=all.size())return;
        Collections.swap(all,at,to);for(int i=0;i<all.size();i++){Banner b=all.get(i);if(b.sortOrder!=i+1){b.sortOrder=i+1;db.save(b);}}db.audit(actor,"MOVE_BANNER",id+(delta<0?" up":" down"));
    }
    @Transactional public boolean toggle(Long actor,Long id){Banner b=db.lock(Banner.class,id);b.active=!b.active;db.save(b);db.audit(actor,b.active?"ENABLE_BANNER":"DISABLE_BANNER",String.valueOf(id));return b.active;}
    @Transactional public void delete(Long actor,Long id){Banner b=db.lock(Banner.class,id);db.delete(b);db.audit(actor,"DELETE_BANNER",String.valueOf(id));}
}
