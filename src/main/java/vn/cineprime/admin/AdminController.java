package vn.cineprime.admin;

import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;
import vn.cineprime.auth.SecurityConfig.SessionUser;
import vn.cineprime.booking.BookingService;
import vn.cineprime.domain.Store;
import vn.cineprime.media.MediaService;
import vn.cineprime.payment.MomoService;
import vn.cineprime.web.Catalog;
import static vn.cineprime.domain.Domain.*;

@Controller @RequestMapping("/admin")
public class AdminController {
    private String newShowtimeUrl(Map<String,String> values,String returnTo){
        Map<String,String> selected=new HashMap<>();
        if(returnTo!=null){var params=UriComponentsBuilder.fromUriString(safeReturn("showtimes",returnTo)).build().getQueryParams();for(String name:List.of("cinemaId","roomId","movieId","date"))if(params.getFirst(name)!=null)selected.put(name,params.getFirst(name));}
        selected.putAll(values);if(!selected.containsKey("date")&&selected.getOrDefault("startsAt","").length()>=10)selected.put("date",selected.get("startsAt").substring(0,10));
        var url=UriComponentsBuilder.fromPath("/admin/showtimes/new");
        for(String name:List.of("cinemaId","roomId","movieId"))if(selected.getOrDefault(name,"").matches("[1-9][0-9]*"))url.queryParam(name,selected.get(name));
        try{if(selected.containsKey("date"))url.queryParam("date",LocalDate.parse(selected.get("date")));}catch(java.time.format.DateTimeParseException ignored){}
        try{if(selected.containsKey("requestKey"))url.queryParam("requestKey",UUID.fromString(selected.get("requestKey")));}catch(IllegalArgumentException ignored){}
        return url.build().encode().toUriString();
    }
    @GetMapping("/showtimes/batch") public String oldBatch(@RequestParam Map<String,String> query){return "redirect:"+newShowtimeUrl(query,query.get("returnTo"));}
    @GetMapping("/showtimes/new") public String batch(@RequestParam(required=false)Long cinemaId,@RequestParam(required=false)Long roomId,@RequestParam(required=false)Long movieId,@RequestParam(required=false)LocalDate date,Model model){
        if(roomId!=null)cinemaId=setup.room(cinemaId,roomId).cinemaId;
        var cinemas=db.list(Cinema.class,"order by e.name,e.id");if(cinemaId==null&&!cinemas.isEmpty())cinemaId=cinemas.getFirst().id;if(cinemaId!=null)db.get(Cinema.class,cinemaId);
        model.addAttribute("batchRoomId",Objects.toString(roomId,""));model.addAttribute("batchRooms",cinemaId==null?List.of():db.views(db.list(Room.class,"where e.cinemaId=?1 order by e.name,e.id",cinemaId)));model.addAttribute("batchCinemaId",Objects.toString(cinemaId,""));model.addAttribute("batchMovieId",Objects.toString(movieId,""));model.addAttribute("batchDate",(date==null?clock.instant().atZone(ReportService.ZONE).toLocalDate():date).toString());model.addAttribute("batchMovies",db.views(db.list(Movie.class,"order by e.title,e.id")));model.addAttribute("title","Thêm suất chiếu · CinePrime Admin");model.addAttribute("resourceLabel","Thêm suất chiếu");return "admin/showtime-batch";
    }
    private final Store db;private final AdminService admin;private final Catalog catalog;private final BookingService bookings;private final MomoService momo;private final MediaService media;private final Clock clock;private final ReportService reports;private final OperationsService operations;private final AdminPresentation presentation;private final AdminReads reads;private final ShowtimeEditor editor;private final MovieGenres genres;private final RoomSetup setup;private final MovieCredits credits;
    public AdminController(Store db,AdminService admin,Catalog catalog,BookingService bookings,MomoService momo,MediaService media,Clock clock,ReportService reports,OperationsService operations,AdminPresentation presentation,AdminReads reads,ShowtimeEditor editor,MovieGenres genres,RoomSetup setup,MovieCredits credits){this.db=db;this.admin=admin;this.catalog=catalog;this.bookings=bookings;this.momo=momo;this.media=media;this.clock=clock;this.reports=reports;this.operations=operations;this.presentation=presentation;this.reads=reads;this.editor=editor;this.genres=genres;this.setup=setup;this.credits=credits;}
    private record NavEntry(String id,String label,String section,List<String[]> tabs){}
    private static NavEntry entry(String id,String label,String section,String... links){List<String[]> tabs=new ArrayList<>();for(int i=0;i<links.length;i+=2)tabs.add(new String[]{links[i],links[i+1]});return new NavEntry(id,label,section,List.copyOf(tabs));}
    // Flat sidebar (one click per area); areas with several screens show them as tabs at the top of the page. Every tab keeps its own URL.
    private static final List<NavEntry> NAV=List.of(entry("overview","Tổng quan","","Tổng quan & Báo cáo","/admin"),entry("orders","Đơn hàng","","Đơn hàng","/admin/orders"),
        entry("movies","Phim","Vận hành","Phim","/admin/manage/movies"),entry("schedule","Lịch chiếu","Vận hành","Lịch theo ngày","/admin/schedule","Suất chiếu","/admin/manage/showtimes"),
        entry("cinemas","Rạp & phòng","Vận hành","Rạp","/admin/manage/cinemas","Phòng","/admin/manage/rooms","Ghế / Bảo trì","/admin/manage/seats"),entry("shifts","Ca tại quầy","Vận hành","Ca nhân viên tại quầy","/admin/shifts"),
        entry("business","Kinh doanh","Quản lý","Bảng giá","/admin/manage/prices","Bắp nước / Combo","/admin/manage/products","Voucher","/admin/manage/vouchers"),
        entry("content","Nội dung","Quản lý","Banner trang chủ","/admin/banners","Bài viết / Ưu đãi / Chính sách","/admin/manage/contents","Bình luận","/admin/manage/reviews"),
        entry("system","Hệ thống","Quản lý","Người dùng","/admin/manage/users","Nhật ký hoạt động","/admin/audit"));
    private static final Set<String> CINEMA_SCOPED=Set.of("/admin/schedule","/admin/manage/showtimes","/admin/manage/rooms","/admin/manage/seats","/admin/manage/prices");
    private static final Map<String,String> ADD_LABELS=Map.of("movies","phim","cinemas","rạp","rooms","phòng","prices","giá vé","products","sản phẩm","vouchers","voucher","contents","nội dung","users","tài khoản");
    private static boolean selected(String href,String path){if(href.equals("/admin"))return path.equals(href);if(path.equals(href)||path.startsWith(href+"/"))return true;return switch(href){case "/admin/manage/showtimes"->path.equals("/admin/showtimes/batch")||path.equals("/admin/showtimes/new");case "/admin/manage/rooms"->path.startsWith("/admin/layout")||path.startsWith("/admin/setup/rooms");case "/admin/manage/vouchers"->path.startsWith("/admin/voucher-usage");default->false;};}
    @ModelAttribute public void nav(Model model,HttpServletRequest request){
        String path=request.getServletPath();if(path.equals("/admin/operations")||path.equals("/admin/media")||path.endsWith(".csv")||path.endsWith("/dialog"))return;
        String cinemaParam=request.getParameter("cinemaId");if(cinemaParam!=null&&!cinemaParam.matches("\\d{1,18}"))cinemaParam=null;
        List<Map<String,Object>> items=new ArrayList<>();String section="Quản trị",hubLabel=null,previous="";Map<String,Object> hub=null;
        for(NavEntry e:NAV){
            String activeTab=e.tabs().stream().filter(t->selected(t[1],path)).map(t->t[0]).findFirst().orElse(null);
            Map<String,Object> item=new HashMap<>();item.put("id",e.id());item.put("label",e.label());item.put("href",e.tabs().getFirst()[1]);item.put("active",activeTab!=null);item.put("sectionStart",e.section().equals(previous)?"":e.section());previous=e.section();items.add(item);
            if(activeTab==null)continue;section=activeTab;
            // Tabs only on the list screens themselves; edit forms and sub-pages keep their own heading.
            if(e.tabs().size()>1&&e.tabs().stream().anyMatch(t->t[1].equals(path))){String cinema=cinemaParam;hubLabel=e.label();
                hub=Map.of("label",e.label(),"tabs",e.tabs().stream().map(t->Map.<String,Object>of("label",t[0],"href",cinema!=null&&CINEMA_SCOPED.contains(t[1])?t[1]+"?cinemaId="+cinema:t[1],"active",t[1].equals(path))).toList());}
        }
        model.addAttribute("resourceLabel",section);model.addAttribute("title",section+" · CinePrime Admin");model.addAttribute("navItems",items);if(hub!=null){model.addAttribute("hub",hub);model.addAttribute("hubLabel",hubLabel);}
        if(path.startsWith("/admin/manage/"))model.addAttribute("addLabel","+ Thêm "+ADD_LABELS.getOrDefault(path.substring("/admin/manage/".length()),"mới"));
        model.addAttribute("adminCinemas",db.views(db.list(Cinema.class,"order by e.name")));model.addAttribute("adminRooms",db.query("select r.id,r.name,c.name from %s r, %s c where r.cinemaId=c.id order by c.name,r.name".formatted(db.entityName(Room.class),db.entityName(Cinema.class)),Object[].class).stream().map(row->Map.of("id",row[0],"name",row[2]+" · "+row[1])).toList());model.addAttribute("operationsRole","Quản trị");
    }
    @GetMapping public String dashboard(@RequestParam(defaultValue="day")String period,@RequestParam(required=false)LocalDate date,@RequestParam(required=false)Long cinemaId,HttpSession session,Model model){var snapshot=reports.create(period,date,cinemaId);reports.remember(session,snapshot);model.addAllAttributes(snapshot.model());for(String section:List.of("chart","topMovies","occupancy"))model.addAttribute(section+"Page",reports.section(snapshot,section,0).model());model.addAttribute("chartWidth",((List<?>)snapshot.model().get("chart")).size()*32+24);model.addAttribute("operations",operations.read(cinemaId));model.addAttribute("title","Tổng quan · CinePrime Admin");return "admin/dashboard";}
    @GetMapping("/revenue/{id}.csv") public ResponseEntity<byte[]> revenue(@PathVariable String id,HttpSession session){return download("cineprime-revenue-"+id+".csv",reports.recall(session,id).csv());}
    @GetMapping("/operations") @ResponseBody public Map<String,Object> operations(@RequestParam(required=false)Long cinemaId){return operations.read(cinemaId);}
    @GetMapping("/manage/{key}") public String list(@PathVariable String key,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="")String state,@RequestParam(defaultValue="")String activity,@RequestParam(required=false)Long cinemaId,@RequestParam(required=false)Long roomId,@RequestParam(required=false)Long movieId,@RequestParam(required=false)LocalDate date,@RequestParam(defaultValue="")String dayType,@RequestParam(defaultValue="0")int page,@AuthenticationPrincipal SessionUser user,HttpServletRequest request,Model model){
        var resource=admin.resource(key);boolean scoped=Set.of("rooms","seats","showtimes","prices").contains(key);
        if(scoped){if(cinemaId==null&&roomId!=null){Long inferred=db.get(Room.class,roomId).cinemaId;return "redirect:"+UriComponentsBuilder.fromPath(request.getServletPath()).queryParam("cinemaId",inferred).queryParam("roomId",roomId).queryParam("q",q).queryParam("state",state).queryParam("activity",activity).build().encode().toUriString();}Long chosen=cinema(cinemaId,user,request.getSession());if(!Objects.equals(chosen,cinemaId))return contextRedirect(request,chosen);cinemaId=chosen;context(model,cinemaId);
            if(roomId!=null){Long scope=cinemaId;if(db.first(Room.class,"where e.id=?1 and e.cinemaId=?2",roomId,scope).isEmpty())return contextRedirect(request,cinemaId);}
        }
        LocalDate day=date==null?clock.instant().atZone(ReportService.ZONE).toLocalDate():date;
        if(key.equals("showtimes")){var movies=reads.movies(cinemaId,day);if(movieId!=null){Long selected=movieId;if(movies.stream().noneMatch(m->m.get("id").equals(selected)))return contextRedirect(request,cinemaId);}model.addAttribute("filterMovies",movies);}
        if(key.equals("users")&&!Set.of("CUSTOMER","STAFF","ADMIN").contains(state))state="CUSTOMER";
        model.addAllAttributes(presentation.list(key,q,state,activity,cinemaId,roomId,movieId,key.equals("showtimes")?day:null,dayType,page));model.addAttribute("adminForm",true);model.addAttribute("key",key);model.addAttribute("resourceLabel",resource.label());model.addAttribute("title",resource.label()+" · CinePrime Admin");model.addAttribute("q",q);model.addAttribute("state",state);model.addAttribute("cinemaId",cinemaId==null?"":cinemaId.toString());model.addAttribute("roomId",roomId==null?"":roomId.toString());model.addAttribute("movieId",movieId==null?"":movieId.toString());model.addAttribute("day",day);model.addAttribute("dayType",dayType);model.addAttribute("states",presentation.states(key));model.addAttribute("activity",activity);model.addAttribute("hasActivityFilter",Set.of("movies","products","contents","vouchers","users","seats").contains(key));
        model.addAttribute("scopedCinema",scoped);model.addAttribute("hasCinemaFilter",Set.of("cinemas","rooms","seats","showtimes","prices","users").contains(key));model.addAttribute("hasRoomFilter",Set.of("seats","showtimes").contains(key));
        var url=UriComponentsBuilder.fromPath("/admin/manage/"+key).queryParam("q",q).queryParam("state",state).queryParam("activity",activity).queryParam("dayType",dayType).queryParamIfPresent("cinemaId",Optional.ofNullable(cinemaId)).queryParamIfPresent("roomId",Optional.ofNullable(roomId)).queryParamIfPresent("movieId",Optional.ofNullable(movieId));if(key.equals("showtimes"))url.queryParam("date",day);
        if(key.equals("showtimes"))model.addAttribute("schedulePeerLink",scheduleUrl("/admin/schedule",cinemaId,roomId,movieId,day,q,state));String base=url.build().encode().toUriString();model.addAttribute("listBase",base);String returnTo=base+"&page="+model.getAttribute("page");model.addAttribute("returnTo",returnTo);model.addAttribute("returnEncoded",java.net.URLEncoder.encode(returnTo,StandardCharsets.UTF_8));model.addAttribute("dialogForm",DIALOG_KEYS.contains(key));return "admin/list";
    }
    private Long cinema(Long requested,SessionUser user,HttpSession session){
        if(requested!=null){db.get(Cinema.class,requested);session.setAttribute("admin.cinema."+user.id(),requested);return requested;}
        Object saved=session.getAttribute("admin.cinema."+user.id());if(saved instanceof Long id&&db.first(Cinema.class,"where e.id=?1",id).isPresent())return id;
        return db.first(Cinema.class,"where e.active=true order by e.name,e.id").map(c->c.id).orElse(null);
    }
    private String contextRedirect(HttpServletRequest request,Long cinema){
        var url=UriComponentsBuilder.fromPath(request.getServletPath());request.getParameterMap().forEach((k,v)->{if(!Set.of("cinemaId","roomId","movieId","page").contains(k))for(String value:v)url.queryParam(k,value);});if(cinema!=null)url.queryParam("cinemaId",cinema);return "redirect:"+url.build().encode().toUriString();
    }
    private void context(Model model,Long cinema){
        model.addAttribute("selectedCinemaName",cinema==null?"Chưa có rạp hoạt động":db.get(Cinema.class,cinema).name);
        model.addAttribute("adminRooms",cinema==null?List.of():db.views(db.list(Room.class,"where e.cinemaId=?1 order by e.name,e.id",cinema)));
    }
    @GetMapping("/manage/{key}/edit") public String edit(@PathVariable String key,@RequestParam(required=false)Long id,@RequestParam(defaultValue="")String returnTo,@RequestParam Map<String,String> query,Model model){if(key.equals("showtimes")&&id==null)return "redirect:"+newShowtimeUrl(query,returnTo);model.addAttribute("formQuery",query);return form(key,id,null,returnTo,model);}
    /** Short records that add/edit in a pop-up on their list page; saving still goes through /manage/{key}/save. */
    static final Set<String> DIALOG_KEYS=Set.of("genres","people","movie-genres","credits","contents","users");
    @SuppressWarnings("unchecked")
    @GetMapping("/manage/{key}/dialog") public String dialog(@PathVariable String key,@RequestParam(required=false)Long id,@RequestParam(defaultValue="")String returnTo,Model model){
        if(!DIALOG_KEYS.contains(key))throw new IllegalArgumentException("Mục này không mở dạng pop-up.");var resource=admin.resource(key);
        model.addAttribute("key",key);model.addAttribute("resourceLabel",resource.label());model.addAttribute("rowId",id);model.addAttribute("returnTo",safeReturn(key,returnTo));model.addAttribute("required",AdminPresentation.required(key));
        model.addAttribute("fields",presentation.groups(key,id,null).stream().flatMap(g->((List<Map<String,Object>>)g.get("fields")).stream()).toList());
        return "admin/form-dialog";
    }
    private String form(String key,Long id,Map<String,String> values,String returnTo,Model model){var resource=admin.resource(key);model.addAttribute("adminForm",true);model.addAttribute("key",key);model.addAttribute("resourceLabel",resource.label());model.addAttribute("rowId",id);formContext(key,id,values,returnTo,model);if(values==null&&model.getAttribute("initialValues") instanceof Map)values=(Map<String,String>)model.getAttribute("initialValues");if(key.equals("showtimes")){boolean initial=values==null;values=editor.values(id,values,safeReturn(key,returnTo));if(initial&&id==null&&model.getAttribute("formQuery") instanceof Map query){for(String n:List.of("cinemaId","roomId","movieId")){Object v=query.get(n);if(v!=null&&v.toString().matches("[0-9]+"))values.put(n,v.toString());}if(longId(values.get("roomId"))!=null){Room selected=setup.room(longId(Objects.toString(query.get("cinemaId"),"")),longId(values.get("roomId")));values.put("cinemaId",selected.cinemaId.toString());}}model.addAllAttributes(editor.form(id,values));model.addAttribute("editorAllRooms",db.views(db.list(Room.class,"order by e.name,e.id")));}if(key.equals("prices")&&id!=null)model.addAttribute("priceVersion",values!=null&&values.containsKey("priceVersion")?values.get("priceVersion"):RoomPrices.version(db.get(Price.class,id)));model.addAttribute("groups",presentation.groups(key,id,values));model.addAttribute("returnTo",safeReturn(key,returnTo));model.addAttribute("title",resource.label()+" · CinePrime Admin");String lock="";if(id!=null){if(key.equals("rooms")&&admin.busyRoom(id))lock="Phòng đang có lịch. Các thay đổi bị giới hạn để bảo vệ vé và lịch sử.";if(key.equals("seats")&&admin.busyRoom(db.get(Seat.class,id).roomId))lock="Sơ đồ phòng đang khóa. Backend sẽ từ chối thay đổi ghế.";if(key.equals("showtimes")&&admin.busyShow(id))lock="Suất có lượt giữ/vé bán. Không thể đổi phim, phòng, giờ hoặc hủy suất.";}model.addAttribute("lockReason",lock);model.addAttribute("fullyLocked",!lock.isEmpty()&&Set.of("rooms","seats").contains(key));return key.equals("movies")?"admin/movie-form":"admin/edit";}
    private void formContext(String key,Long id,Map<String,String> submitted,String returnTo,Model model){
        Map<String,String> query=new HashMap<>();var params=UriComponentsBuilder.fromUriString(safeReturn(key,returnTo)).build().getQueryParams();params.forEach((k,v)->query.put(k,v.getFirst()));if(model.getAttribute("formQuery") instanceof Map q)query.putAll(q);if(submitted!=null)query.putAll(submitted);
        if(key.equals("movies")){Set<Long> selected=submitted!=null&&submitted.containsKey("genresPresent")?MovieGenres.parse(submitted.get("genreIds")):genres.ids(id);var options=genres.options(id,selected);model.addAttribute("movieGenres",options);model.addAttribute("movieDirectors",credits.selected(id,submitted,"DIRECTOR"));model.addAttribute("movieActors",credits.selected(id,submitted,"ACTOR"));model.addAttribute("genreWarning",options.stream().noneMatch(o->Boolean.TRUE.equals(o.get("active"))&&Boolean.TRUE.equals(o.get("selected"))));
            model.addAttribute("mv",presentation.values(key,id,submitted));model.addAttribute("movieToday",vn.cineprime.domain.MovieLifecycle.today(clock).toString());
            List<Showtime> shows=id==null?List.of():db.list(Showtime.class,"where e.movieId=?1 and e.status<>'CANCELLED' order by e.startsAt",id);
            model.addAttribute("movieShows",Map.of("count",shows.size(),"first",shows.isEmpty()?"":shows.getFirst().startsAt.atZone(ReportService.ZONE).toLocalDate().toString(),"last",shows.isEmpty()?"":shows.getLast().startsAt.atZone(ReportService.ZONE).toLocalDate().toString(),"upcoming",shows.stream().filter(s->s.endsAt.isAfter(clock.instant())).count()));}
        if(key.equals("genres")&&id!=null){int page=number(query.get("relatedPage"));model.addAttribute("genreRelated",genres.related(id,page));}
        if(!Set.of("rooms","seats","prices").contains(key))return;
        Map<String,String> values=submitted==null?new HashMap<>():new HashMap<>(submitted);Row row=id==null?null:db.get(admin.resource(key).type(),id);
        if(submitted==null){try{if(row==null)row=admin.resource(key).type().getConstructor().newInstance();for(String name:admin.resource(key).fields().split(",")){Object value=row.getClass().getField(name).get(row);values.put(name,Objects.toString(value,""));}if(key.equals("prices")){values.put("amount","");values.put("fromMinute","00:00");values.put("toMinute","24:00");if(id!=null){Price p=(Price)row;values.put("amount",Long.toString(p.amount));values.put("fromMinute",AdminPresentation.time(p.fromMinute));values.put("toMinute",AdminPresentation.time(p.toMinute));}}}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
        Long roomId=key.equals("rooms")?id:key.equals("seats")?longId(values.get("roomId")):longId(query.getOrDefault("setupRoomId",query.get("roomId")));if(roomId==null&&key.equals("seats"))roomId=longId(query.get("roomId"));
        Long cinema=longId(values.get("cinemaId"));if(key.equals("prices")&&roomId!=null&&cinema!=null){Long selectedRoom=roomId,selectedCinema=cinema;if(db.first(Room.class,"where e.id=?1 and e.cinemaId=?2",selectedRoom,selectedCinema).isEmpty())roomId=null;}if(roomId!=null)cinema=setup.room(cinema,roomId).cinemaId;if(cinema==null)cinema=longId(query.get("cinemaId"));if(cinema==null)cinema=db.first(Cinema.class,"where e.active=true order by e.name,e.id").map(c->c.id).orElse(null);
        if(id==null&&submitted==null){if(cinema!=null)values.put("cinemaId",cinema.toString());if(roomId!=null)values.put("roomId",roomId.toString());if(key.equals("prices")){for(String n:List.of("seatType","dayType"))if(query.containsKey(n))values.put(n,query.get(n));}}
        model.addAttribute("editingContext",setup.context(cinema,roomId));model.addAttribute("setupRoomId",roomId==null?"":roomId.toString());if(submitted==null)model.addAttribute("initialValues",values);
    }
    private Long longId(String v){return v!=null&&v.matches("[0-9]+")?Long.valueOf(v):null;}
    private int number(String v){try{return Math.max(0,Integer.parseInt(Objects.toString(v,"0")));}catch(NumberFormatException e){return 0;}}
    private String safeReturn(String key,String path){String base="/admin/manage/"+key;return path!=null&&(path.equals(base)||path.startsWith(base+"?")||key.equals("showtimes")&&(path.equals("/admin/schedule")||path.startsWith("/admin/schedule?")))&&!path.contains("\r")&&!path.contains("\n")?path:base;}
    @PostMapping("/manage/{key}/save") public Object save(@AuthenticationPrincipal SessionUser user,@PathVariable String key,@RequestParam(required=false)Long id,@RequestParam org.springframework.util.MultiValueMap<String,String> fields,@RequestHeader(name="X-Admin-Form",defaultValue="")String mode,Model model,HttpServletResponse response){
        Map<String,String> values=new HashMap<>(fields.toSingleValueMap());
        for(String name:List.of("genreIds","directorIds","actorIds"))if(fields.containsKey(name))values.put(name,String.join(",",fields.get(name)));
        String returnTo=safeReturn(key,values.get("returnTo"));
        if(key.equals("showtimes")&&id==null){String target=newShowtimeUrl(values,returnTo);if(mode.equals("steps"))return ResponseEntity.status(409).body(Map.of("code","SINGLE_CREATE_REMOVED","message","Hãy thêm suất chiếu bằng lịch tuần.","redirect",target));return "redirect:"+target+(target.contains("?")?"&":"?")+"legacyCreateRemoved";}
        try{
            if(key.equals("prices")&&values.getOrDefault("setupRoomId","").matches("[0-9]+"))setup.room(longId(values.get("cinemaId")),Long.valueOf(values.get("setupRoomId")));
            Row saved=admin.save(user.id(),key,id,AdminPresentation.normalize(values));
            if(saved instanceof Showtime show)returnTo=showtimeReturn(returnTo,show);
            String target=returnTo+(returnTo.contains("?")?"&":"?")+"saved";
            if(saved instanceof Room r)target="continue".equals(values.get("next"))?"/admin/setup/rooms/"+r.id:"/admin/manage/rooms?cinemaId="+r.cinemaId+"&saved";
            if("continue".equals(values.get("next"))&&saved instanceof Cinema c)target="/admin/manage/rooms/edit?cinemaId="+c.id;
            if(saved instanceof Price p&&values.getOrDefault("setupRoomId","").matches("[0-9]+")){Room r=setup.room(p.cinemaId,Long.valueOf(values.get("setupRoomId")));target="/admin/setup/rooms/"+r.id+"?step=prices&saved";}
            return mode.equals("steps")?ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("redirect",target)):"redirect:"+target;
        }catch(IllegalArgumentException|org.springframework.dao.DataAccessException error){
            int code=error instanceof org.springframework.dao.DataAccessException?409:400;
            String message=error instanceof org.springframework.dao.DataAccessException?"Dữ liệu bị trùng hoặc vừa thay đổi. Kiểm tra lại thông tin; nội dung nhập vẫn được giữ.":error.getMessage();
            List<String> locations=error instanceof AdminFormProblem problem?problem.fields():List.of();
            if(mode.equals("steps"))return ResponseEntity.status(code).cacheControl(CacheControl.noStore()).body(Map.of("message",message,"fields",locations,"passwordRequired",key.equals("users")&&id==null));
            response.setStatus(code);model.addAttribute("formError",message);model.addAttribute("errorFields",String.join(",",locations));return form(key,id,values,returnTo,model);
        }
    }
    private String showtimeReturn(String path,Showtime show){var url=UriComponentsBuilder.fromUriString(path);var params=url.build().getQueryParams();String cinema=db.get(Room.class,show.roomId).cinemaId.toString(),day=show.startsAt.atZone(ReportService.ZONE).toLocalDate().toString();if(cinema.equals(params.getFirst("cinemaId"))&&day.equals(Objects.toString(params.getFirst("date"),clock.instant().atZone(ReportService.ZONE).toLocalDate().toString())))return path;return url.replaceQueryParam("cinemaId",cinema).replaceQueryParam("date",day).replaceQueryParam("roomId",show.roomId).replaceQueryParam("movieId",show.movieId).replaceQueryParam("page",0).replaceQueryParam("q","").replaceQueryParam("state","").build().encode().toUriString();}
    @PostMapping("/manage/{key}/{id}/disable") public String disable(@AuthenticationPrincipal SessionUser user,@PathVariable String key,@PathVariable Long id,@RequestParam(defaultValue="")String returnTo,org.springframework.web.servlet.mvc.support.RedirectAttributes flash){try{admin.remove(user.id(),key,id);flash.addFlashAttribute("notice",key.equals("movies")?"Đã ẩn phim khỏi website. Đơn và vé hiện có được giữ nguyên.":"Đã cập nhật trạng thái.");}catch(IllegalArgumentException|org.springframework.dao.DataAccessException e){flash.addFlashAttribute("actionError",e instanceof IllegalArgumentException?e.getMessage():"Dữ liệu vừa thay đổi hoặc còn liên kết. Vui lòng kiểm tra lại.");}return "redirect:"+safeReturn(key,returnTo);}
    @PostMapping("/manage/movies/{id}/{action:restore|delete}") public String movieAction(@AuthenticationPrincipal SessionUser user,@PathVariable Long id,@PathVariable String action,@RequestParam(defaultValue="")String returnTo,org.springframework.web.servlet.mvc.support.RedirectAttributes flash){
        try{String name=admin.movieAction(user.id(),id,action);flash.addFlashAttribute("notice",(action.equals("delete")?"Đã xóa phim chưa sử dụng: ":"Đã hiện lại phim: ")+name);}
        catch(IllegalArgumentException|org.springframework.dao.DataAccessException e){flash.addFlashAttribute("actionError",e instanceof IllegalArgumentException?e.getMessage():"Phim vừa có dữ liệu liên quan. Không thể xóa; hãy ẩn phim.");}
        return "redirect:"+safeReturn("movies",returnTo);
    }
    @PostMapping("/layout") public String layout(@AuthenticationPrincipal SessionUser user,@RequestParam Long roomId,@RequestParam int rows,@RequestParam int columns){admin.generateSeats(user.id(),roomId,rows,columns);return "redirect:/admin/manage/seats?roomId="+roomId;}
    /** Inline upload used by every admin form's image/trailer field; the old standalone Upload page is gone. */
    @PostMapping("/media") @ResponseBody public Map<String,Object> media(@RequestParam MultipartFile file,@RequestParam String kind)throws Exception{String url=media.upload(file,kind);return "banner".equals(kind)?Map.of("url",url,"warning",media.bannerWarning(url)):Map.of("url",url);}
    @GetMapping("/voucher-usage/{id}") public String voucherUsage(@PathVariable Long id,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="")String state,@RequestParam(defaultValue="0")int page,Model model){
        model.addAttribute("voucher",db.view(db.get(Voucher.class,id)));model.addAllAttributes(reads.voucherUsage(id,q,state,page));model.addAttribute("q",q);model.addAttribute("state",state);return "admin/voucher-usage";
    }
    private String scheduleUrl(String path,Long cinema,Long room,Long movie,LocalDate day,String q,String state){
        return UriComponentsBuilder.fromPath(path).queryParamIfPresent("cinemaId",Optional.ofNullable(cinema)).queryParamIfPresent("roomId",Optional.ofNullable(room)).queryParamIfPresent("movieId",Optional.ofNullable(movie)).queryParam("date",day).queryParam("q",q).queryParam("state",state).build().encode().toUriString();
    }
    @GetMapping("/schedule") public String schedule(@RequestParam(required=false)LocalDate date,@RequestParam(required=false)Long cinemaId,@RequestParam(required=false)Long roomId,@RequestParam(required=false)Long movieId,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="")String state,@RequestParam(defaultValue="0")int page,@AuthenticationPrincipal SessionUser user,HttpServletRequest request,Model model){
        Long chosen=cinemaId==null&&roomId!=null?db.get(Room.class,roomId).cinemaId:cinema(cinemaId,user,request.getSession());
        if(!Objects.equals(chosen,cinemaId)){var target=UriComponentsBuilder.fromPath(request.getServletPath());request.getParameterMap().forEach((k,v)->{if(!Set.of("cinemaId","page").contains(k))for(String value:v)target.queryParam(k,value);});if(chosen!=null)target.queryParam("cinemaId",chosen);return "redirect:"+target.build().encode().toUriString();}
        context(model,chosen);if(roomId!=null&&db.first(Room.class,"where e.id=?1 and e.cinemaId=?2",roomId,chosen).isEmpty())return contextRedirect(request,chosen);
        LocalDate day=date==null?clock.instant().atZone(ReportService.ZONE).toLocalDate():date;var movies=reads.movies(chosen,day);if(movieId!=null){Long selected=movieId;if(movies.stream().noneMatch(m->m.get("id").equals(selected)))return contextRedirect(request,chosen);}
        model.addAllAttributes(reads.schedule(day,chosen,roomId,movieId,q,state,page));String returnTo=scheduleUrl("/admin/schedule",chosen,roomId,movieId,day,q,state)+"&page="+model.getAttribute("page");
        model.addAttribute("returnEncoded",java.net.URLEncoder.encode(returnTo,StandardCharsets.UTF_8));model.addAttribute("schedulePeerLink",scheduleUrl("/admin/manage/showtimes",chosen,roomId,movieId,day,q,state));model.addAttribute("filterMovies",movies);model.addAttribute("day",day);model.addAttribute("movieId",movieId==null?"":movieId.toString());model.addAttribute("roomId",roomId==null?"":roomId.toString());model.addAttribute("cinemaId",chosen==null?"":chosen.toString());model.addAttribute("q",q);model.addAttribute("state",state);return "admin/schedule";
    }
    @GetMapping("/layout/{id}") public String layoutPreview(@PathVariable Long id,Model model){db.get(Room.class,id);return "redirect:/admin/setup/rooms/"+id;}
    @GetMapping("/orders") public String orders(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="")String state,@RequestParam(defaultValue="")String paymentState,@RequestParam(defaultValue="")String channel,@RequestParam(required=false)Long cinemaId,@RequestParam(required=false)LocalDate date,@RequestParam(defaultValue="0")int page,Model model){
        model.addAllAttributes(reads.orders(q,state,paymentState,channel,cinemaId,date,page));model.addAttribute("channel",channel);model.addAttribute("q",q);model.addAttribute("state",state);model.addAttribute("paymentState",paymentState);model.addAttribute("cinemaId",cinemaId==null?"":cinemaId.toString());model.addAttribute("day",date==null?"":date.toString());return "admin/orders";
    }
    @GetMapping("/orders/{id}") public String order(@PathVariable Long id,@RequestParam(defaultValue="0")int linePage,@RequestParam(defaultValue="0")int comboPage,@RequestParam(defaultValue="0")int paymentPage,@RequestParam(defaultValue="0")int ticketPage,Model model){
        Order order=db.get(Order.class,id);var view=catalog.order(order);view.put("statusLabel",AdminPresentation.text(order.status));model.addAttribute("order",view);model.addAttribute("customer",order.userId==null?Map.of("name","Khách vãng lai","email",""):db.view(db.get(User.class,order.userId)));model.addAttribute("counter",counterInfo(order));model.addAttribute("lines",reads.orderSection(id,"lines",linePage));model.addAttribute("combos",reads.orderSection(id,"combos",comboPage));model.addAttribute("payments",reads.orderSection(id,"payments",paymentPage));model.addAttribute("tickets",reads.orderSection(id,"tickets",ticketPage));return "admin/order";
    }
    @PostMapping("/orders/{id}/cancel") public String cancel(@AuthenticationPrincipal SessionUser user,@PathVariable Long id,@RequestParam(required=false)Long viewRoomId,@RequestParam(required=false)Long viewShowtimeId,org.springframework.web.servlet.mvc.support.RedirectAttributes flash){
        Order order=db.get(Order.class,id);String back="/admin/orders/"+id;
        if(viewRoomId!=null&&viewShowtimeId!=null){Showtime show=db.get(Showtime.class,viewShowtimeId);if(!order.showtimeId.equals(show.id)||!show.roomId.equals(viewRoomId))throw new IllegalArgumentException("Đơn không thuộc suất/phòng đang xem.");back="/admin/setup/rooms/"+viewRoomId+"?step=inventory&showtimeId="+show.id;}
        try{bookings.expire(id);bookings.cancelUnpaid(id);db.audit(user.id(),"ADMIN_CANCEL",order.code);flash.addFlashAttribute("notice","Đã hủy toàn bộ đơn "+order.code+" và giải phóng ghế/lượt voucher.");}catch(IllegalArgumentException|org.springframework.dao.DataAccessException e){flash.addFlashAttribute("actionError",e instanceof IllegalArgumentException?e.getMessage():"Đơn vừa thay đổi. Đọc lại trạng thái trước khi thao tác.");}return "redirect:"+back;
    }
    @PostMapping("/payments/{id}/verify") public String verify(@AuthenticationPrincipal SessionUser user,@PathVariable Long id){momo.query(id);db.audit(user.id(),"VERIFY_PAYMENT",id.toString());return "redirect:/admin/orders/"+db.get(Payment.class,id).orderId;}
    @GetMapping("/shifts") public String shifts(@RequestParam(required=false)Long cinemaId,@RequestParam(required=false)LocalDate date,@RequestParam(defaultValue="")String state,@RequestParam(defaultValue="0")int page,Model model){
        if(!state.isBlank()&&!Set.of("OPEN","CLOSED").contains(state))throw new IllegalArgumentException("Trạng thái ca không hợp lệ.");
        model.addAllAttributes(reads.shifts(cinemaId,date,state,page));model.addAttribute("openShifts",operations.page("shifts",cinemaId,""));model.addAttribute("cinemaId",cinemaId==null?"":cinemaId.toString());model.addAttribute("day",date==null?"":date.toString());model.addAttribute("state",state);return "admin/shifts";
    }
    @GetMapping("/audit") public String audit(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="0")int page,Model model){model.addAllAttributes(reads.audit(q,page));model.addAttribute("q",q);return "admin/audit";}
    private Map<String,Object> counterInfo(Order order){
        if(!"COUNTER".equals(order.channel))return Map.of("counter",false,"channelLabel","Online");
        Map<String,Object> v=new LinkedHashMap<>();v.put("counter",true);v.put("channelLabel","Bán tại quầy");v.put("seller",order.soldBy==null?"—":db.get(User.class,order.soldBy).name);v.put("shift",order.shiftId==null?"—":"#"+order.shiftId);
        Optional<Payment> cash=db.first(Payment.class,"where e.orderId=?1 and e.provider='CASH' order by e.id desc",order.id);v.put("received",cash.map(p->db.cash(p.received==null?p.amount:p.received)).orElse("—"));v.put("change",cash.map(p->db.cash((p.received==null?p.amount:p.received)-p.amount)).orElse("—"));
        v.put("cancelled",order.cancelledAt!=null);v.put("cancelledAt",db.date(order.cancelledAt));v.put("cancelledBy",order.cancelledBy==null?"—":db.get(User.class,order.cancelledBy).name);v.put("cancelReason",Objects.toString(order.cancelReason,""));return v;
    }
    private String email(Order order){return order.userId==null?"":db.get(User.class,order.userId).email;}
    @GetMapping("/transactions.csv") public ResponseEntity<byte[]> csv(){StringBuilder out=new StringBuilder("\uFEFFĐơn,Email,Ngày đặt,Trạng thái đơn,Thành tiền,Giao dịch,Trạng thái thanh toán,Đối soát,Kênh\r\n");for(Order order:db.list(Order.class,"order by e.id desc")){String channel=order.channel.equals("COUNTER")?"Quầy":"Online";List<Payment> ps=db.list(Payment.class,"where e.orderId=?1",order.id);if(ps.isEmpty())out.append(ReportService.csvRow(order.code,email(order),db.date(order.createdAt),order.status,order.total,"","","",channel));for(Payment p:ps)out.append(ReportService.csvRow(order.code,email(order),db.date(order.createdAt),order.status,order.total,p.externalOrderId,p.status,p.reconciliation,channel));}return download("cineprime-transactions.csv",out.toString().getBytes(StandardCharsets.UTF_8));}
    private ResponseEntity<byte[]> download(String filename,byte[] bytes){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename="+filename).contentType(new MediaType("text","csv",StandardCharsets.UTF_8)).body(bytes);}
}
