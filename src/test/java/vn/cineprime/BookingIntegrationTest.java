package vn.cineprime;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import static org.junit.jupiter.api.Assertions.*;
import static vn.cineprime.domain.Domain.*;
import vn.cineprime.domain.Domain.Order;
import vn.cineprime.domain.Store;
import vn.cineprime.booking.BookingService;
import vn.cineprime.staff.CheckInService;
import vn.cineprime.admin.AdminService;
import vn.cineprime.auth.AccountService;
import vn.cineprime.media.TicketFiles;
import vn.cineprime.payment.MomoService;

@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches="jdbc:mysql:.*[/]cineprime_test(?:\\?.*)?")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"cineprime.seed=false","cineprime.jobs-enabled=false","cineprime.mail-enabled=false","cineprime.momo.partner-code=TEST_PARTNER","cineprime.momo.access-key=TEST_ACCESS","cineprime.momo.secret-key=TEST_SECRET"})
@Import(BookingIntegrationTest.TimeConfig.class)
class BookingIntegrationTest {
    private final tools.jackson.databind.ObjectMapper json=new tools.jackson.databind.ObjectMapper();
    @Autowired vn.cineprime.admin.ShowtimeBatches batches;
    @Autowired vn.cineprime.notification.Notifications notices;
    @Test void releaseLifecycleUsesVietnamDateInCatalogAdminCountsAndCalendar() {
        Long movie=db.get(Showtime.class,show).movieId;
        tx.executeWithoutResult(s->{Movie m=db.get(Movie.class,movie);m.status="COMING_SOON";m.releaseDate=LocalDate.of(2026,11,9);});
        clock.value.set(Instant.parse("2026-11-08T16:59:59Z"));
        assertEquals(1,catalog.movies(null,"COMING_SOON",null,null,null).size());assertEquals(0,catalog.movies(null,"NOW_SHOWING",null,null,null).size());
        clock.advance(1);
        assertEquals("NOW_SHOWING",catalog.movie(db.get(Movie.class,movie)).get("status"));
        assertEquals(1L,presentation.list("movies","","NOW_SHOWING","",null,null,0).get("total"));
        assertEquals(0L,presentation.list("movies","","COMING_SOON","",null,null,0).get("total"));
        assertEquals(1L,reports.create("day",LocalDate.of(2026,11,9),null).model().get("nowCount"));
        assertTrue(batches.week(cinema,room,LocalDate.of(2026,11,9)).get("movies").toString().contains("status=NOW_SHOWING"));
        assertEquals("COMING_SOON",db.get(Movie.class,movie).status,"Read-time classification must not write the movie");
        tx.executeWithoutResult(s->db.get(Movie.class,movie).releaseDate=LocalDate.of(2026,12,1));
        assertEquals(1,catalog.movies(null,"COMING_SOON",null,null,null).size());
        tx.executeWithoutResult(s->{Movie m=db.get(Movie.class,movie);m.releaseDate=LocalDate.of(2026,11,1);m.endDate=LocalDate.of(2026,11,2);});
        var ended=catalog.movies(null,"ENDED",null,null,null);assertEquals(1,ended.size(),"Ended movies stay listed for seven days");assertEquals(true,ended.getFirst().get("ended"));assertEquals(false,ended.getFirst().get("hasBookableShows"));
        assertEquals(1L,presentation.list("movies","","ENDED","",null,null,0).get("total"));
        tx.executeWithoutResult(s->db.get(Movie.class,movie).endDate=LocalDate.of(2026,11,1));assertEquals(0,catalog.movies(null,null,null,null,null).size(),"Then they leave public lists");
    }
    @Test void effectiveStatusFiltersBeforePaginationAndCountsHiddenAndLegacyCorrectly() {
        tx.executeWithoutResult(s->{for(int n=0;n<7;n++){Movie m=new Movie();m.title="Đến ngày "+n;m.slug="den-ngay-"+n;m.releaseDate=LocalDate.of(2026,11,8);m.endDate=LocalDate.of(2026,12,31);db.save(m);}Movie coming=new Movie();coming.title="Sắp tới";coming.slug="sap-toi";coming.releaseDate=LocalDate.of(2026,12,5);coming.endDate=LocalDate.of(2026,12,31);db.save(coming);Movie over=new Movie();over.title="Đã hết";over.slug="da-het";over.releaseDate=LocalDate.of(2026,11,1);over.endDate=LocalDate.of(2026,11,28);db.save(over);Movie hidden=new Movie();hidden.title="Ẩn";hidden.slug="an";hidden.active=false;hidden.releaseDate=LocalDate.of(2026,11,8);hidden.endDate=LocalDate.of(2026,12,31);db.save(hidden);});
        var first=presentation.list("movies","Đến ngày","NOW_SHOWING","",null,null,0);var next=presentation.list("movies","Đến ngày","NOW_SHOWING","",null,null,1);
        assertEquals(7L,first.get("total"));assertEquals(5,((List<?>)first.get("rows")).size());assertEquals(2,((List<?>)next.get("rows")).size());
        assertEquals(8,catalog.movies(null,"NOW_SHOWING",null,null,null).size());assertEquals(1,catalog.movies(null,"COMING_SOON",null,null,null).size());assertEquals(1,catalog.movies(null,"ENDED",null,null,null).size());
        var model=reports.create("day",LocalDate.of(2026,12,1),null).model();assertEquals(8L,model.get("nowCount"));assertEquals(1L,model.get("comingCount"));
    }
    @Test void releasedWithoutSalesKeepsReminderAndAdvanceSalesRemainBookable() throws Exception {
        Long movie=db.get(Showtime.class,show).movieId;
        tx.executeWithoutResult(s->{db.get(Showtime.class,show).status="DRAFT";Subscription sub=new Subscription();sub.userId=user1;sub.movieId=movie;db.save(sub);});
        assertEquals(false,catalog.movie(db.get(Movie.class,movie)).get("hasBookableShows"));
        String html=org.springframework.web.util.HtmlUtils.htmlUnescape(get(client(),"/movies/phim-kiem-thu").body());assertTrue(html.contains("Đang chiếu · Chưa có lịch mở bán"));assertTrue(html.contains("Nhắc tôi khi mở bán"));
        var worker=new vn.cineprime.notification.Workers(db,booking,notices,null,clock,tm);worker.reminders();assertFalse(db.list(Subscription.class,"").getFirst().sent);
        tx.executeWithoutResult(s->{Showtime sh=db.get(Showtime.class,show);sh.status="OPEN";sh.startsAt=Instant.parse("2026-12-02T03:00:00Z");sh.endsAt=sh.startsAt.plusSeconds(6000);db.get(Movie.class,movie).releaseDate=LocalDate.of(2026,12,2);db.get(Room.class,room).active=false;});
        worker.reminders();assertFalse(db.list(Subscription.class,"").getFirst().sent);
        tx.executeWithoutResult(s->db.get(Room.class,room).active=true);
        assertEquals("COMING_SOON",catalog.movie(db.get(Movie.class,movie)).get("status"));assertEquals(true,catalog.movie(db.get(Movie.class,movie)).get("hasBookableShows"));
        assertDoesNotThrow(()->booking.hold(user2,show,List.of(seat1)));worker.reminders();worker.reminders();assertTrue(db.list(Subscription.class,"").getFirst().sent);assertEquals(1,db.list(Notification.class,"").size());
    }
    @Test void showtimesStayInsideReleaseWindowAndWindowEditsCannotStrandThem() {
        Long movie=db.get(Showtime.class,show).movieId;
        tx.executeWithoutResult(s->{Movie m=db.get(Movie.class,movie);m.releaseDate=LocalDate.of(2026,12,1);m.endDate=LocalDate.of(2026,12,10);});
        var late=batches.preview(batchInput("DRAFT",batchLine("late","2026-12-11","00:05")));assertFalse(late.canCommit());assertTrue(late.rows().getFirst().errors().stream().anyMatch(e->e.code().equals("OUT_OF_RELEASE_WINDOW")),late.rows().toString());
        assertFalse(batches.preview(batchInput("OPEN",batchLine("late-open","2026-12-11","10:00"))).canCommit());
        assertTrue(batches.preview(batchInput("DRAFT",batchLine("last","2026-12-10","23:00"))).canCommit(),"The end date is the last showing day");
        commitBatch(batchInput("DRAFT",batchLine("draft","2026-12-09","10:00")),UUID.randomUUID().toString());
        Map<String,String> moved=new HashMap<>(showFields(db.get(Showtime.class,show)));moved.put("startsAt","2026-12-12T10:00");
        assertEquals(List.of("startsAt"),assertThrows(vn.cineprime.admin.AdminFormProblem.class,()->admin.save(5L,"showtimes",show,moved)).fields());
        Map<String,String> shrink=movieFields(movie);shrink.put("endDate","2026-12-08");
        var stranded=assertThrows(vn.cineprime.admin.AdminFormProblem.class,()->admin.save(5L,"movies",movie,shrink));assertEquals(List.of("endDate"),stranded.fields());assertTrue(stranded.getMessage().contains("1 suất"),stranded.getMessage());
        Map<String,String> later=movieFields(movie);later.put("releaseDate","2026-12-02");assertEquals(List.of("releaseDate"),assertThrows(vn.cineprime.admin.AdminFormProblem.class,()->admin.save(5L,"movies",movie,later)).fields());
        Map<String,String> backwards=movieFields(movie);backwards.put("endDate","2026-11-30");assertEquals(List.of("endDate"),assertThrows(vn.cineprime.admin.AdminFormProblem.class,()->admin.save(5L,"movies",movie,backwards)).fields());
        Map<String,String> missing=movieFields(movie);missing.put("endDate","");assertEquals(List.of("endDate"),assertThrows(vn.cineprime.admin.AdminFormProblem.class,()->admin.save(5L,"movies",movie,missing)).fields());
        Map<String,String> longer=movieFields(movie);longer.put("duration","120");assertEquals(List.of("duration"),assertThrows(vn.cineprime.admin.AdminFormProblem.class,()->admin.save(5L,"movies",movie,longer)).fields(),"Drafts keep their computed end time too");
        assertEquals(LocalDate.of(2026,12,10),db.get(Movie.class,movie).endDate);assertEquals(100,db.get(Movie.class,movie).duration);
        Map<String,String> extend=movieFields(movie);extend.put("endDate","2026-12-20");admin.save(5L,"movies",movie,extend);assertEquals(LocalDate.of(2026,12,20),db.get(Movie.class,movie).endDate);
    }
    @Autowired vn.cineprime.admin.FormOptions formOptions;
    @Test void quickAddPersonFromMovieFormCreatesOnceAndReusesSameName() {
        var admin5=new vn.cineprime.auth.SecurityConfig.SessionUser(5L,"admin@example.test","x","ADMIN",0);
        var first=formOptions.addPerson(admin5,Map.of("name","  Adam   Wingard "));assertEquals(200,first.getStatusCode().value());assertEquals(true,first.getBody().get("created"));
        Long id=(Long)((Map<?,?>)first.getBody().get("person")).get("id");assertEquals("Adam Wingard",db.get(Person.class,id).name);assertTrue(db.get(Person.class,id).active);
        var again=formOptions.addPerson(admin5,Map.of("name","adam wingard"));assertEquals(false,again.getBody().get("created"));assertEquals(id,((Map<?,?>)again.getBody().get("person")).get("id"));
        assertEquals(1L,db.count(Person.class,""));assertEquals(400,formOptions.addPerson(admin5,Map.of("name","   ")).getStatusCode().value());assertEquals(1L,db.count(Person.class,""));
    }
    @Test void sixTimelineSelectionsPublishTogetherAndReplayWithoutDuplicates() {
        var lines=new ArrayList<vn.cineprime.admin.ShowtimeBatch.Line>();for(String date:List.of("2026-12-07","2026-12-08"))for(String time:List.of("09:00","12:00","15:00"))lines.add(batchLine(date+time,date,time));
        var input=new vn.cineprime.admin.ShowtimeBatch.Input(cinema,"MANUAL","OPEN",lines);String key=UUID.randomUUID().toString();var result=commitBatch(input,key);
        assertEquals(6,result.created().size());assertEquals(result,commitBatch(input,key));assertEquals(7,db.count(Showtime.class,"where e.status='OPEN'"));assertEquals(35,db.count(Inventory.class,""));
    }
    @Test void weeklyEntryPointsRedirectAndLegacyCreateCannotWrite()throws Exception{
        HttpClient c=client();login(c,"admin@example.test");String key=UUID.randomUUID().toString();
        String suffix="?cinemaId="+cinema+"&roomId="+room+"&movieId="+db.get(Showtime.class,show).movieId+"&date=2026-12-02&requestKey="+key;
        var old=get(c,"/admin/showtimes/batch"+suffix);assertEquals(302,old.statusCode());String target=old.headers().firstValue("location").orElseThrow();assertEquals("/admin/showtimes/new",URI.create(target).getPath());assertTrue(target.contains("roomId="+room),target);assertTrue(target.contains("requestKey="+key),target);
        var editor=get(c,"/admin/manage/showtimes/edit?returnTo="+URLEncoder.encode("/admin/schedule?cinemaId="+cinema+"&roomId="+room+"&date=2026-12-02",java.nio.charset.StandardCharsets.UTF_8));assertEquals(302,editor.statusCode());assertTrue(editor.headers().firstValue("location").orElseThrow().contains("date=2026-12-02"));
        String page=get(c,"/admin/showtimes/new"+suffix).body(),token=csrf(page);assertTrue(page.contains("/js/showtime-calendar.js"));assertFalse(page.contains("Cách nhập"));
        var fields=new HashMap<>(showFields(db.get(Showtime.class,show)));fields.put("startsAt","2026-12-02T10:00");fields.put("_csrf",token);String body=formBody(fields);
        assertEquals(302,post(c,"/admin/manage/showtimes/save",body).statusCode());
        var json=c.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/admin/manage/showtimes/save")).header("Content-Type","application/x-www-form-urlencoded").header("X-Admin-Form","steps").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());assertEquals(409,json.statusCode());assertTrue(json.body().contains("SINGLE_CREATE_REMOVED"));
        assertEquals("SINGLE_CREATE_REMOVED",assertThrows(vn.cineprime.admin.ShowtimeBatch.Failure.class,()->admin.save(user1,"showtimes",null,fields)).code);assertEquals(1,db.count(Showtime.class,""));
        assertEquals(200,get(c,"/admin/manage/showtimes/edit?id="+show).statusCode());
        for(String path:List.of("/admin/manage/showtimes?cinemaId="+cinema,"/admin/schedule?cinemaId="+cinema,"/admin/setup/rooms/"+room)){String html=get(c,path).body();assertTrue(html.contains("/admin/showtimes/new?"),path);assertFalse(html.contains("href=\"/admin/manage/showtimes/edit?returnTo="),path);}
    }
    @Test void weeklyCalendarKeepsBoundaryCleanupLookaheadAndRoomScope(){
        commitBatch(batchInput("DRAFT",batchLine("before","2026-12-06","22:25")),UUID.randomUUID().toString());commitBatch(batchInput("DRAFT",batchLine("after","2026-12-14","02:00")),UUID.randomUUID().toString());
        var week=batches.week(cinema,room,LocalDate.of(2026,12,7));assertEquals("2026-12-14",week.get("rangeEnd"));assertEquals(2,((List<?>)week.get("events")).size());assertTrue(week.toString().contains("2026-12-07T00:20"));assertTrue(week.toString().contains("2026-12-14T02:00"));
        assertThrows(IllegalArgumentException.class,()->batches.week(cinema,room,LocalDate.of(2026,12,8)));
        Long foreign=tx.execute(s->{Room r=new Room();r.name="Other";r.cinemaId=db.get(User.class,otherStaff).cinemaId;return db.save(r).id;});assertThrows(IllegalArgumentException.class,()->batches.week(cinema,foreign,LocalDate.of(2026,12,7)));
        tx.executeWithoutResult(s->db.get(Movie.class,db.get(Showtime.class,show).movieId).duration=600);
        assertFalse(batches.preview(batchInput("DRAFT",batchLine("late","2026-12-13","23:45"))).canCommit());
    }
    @Test void weeklyCalendarQueriesDoNotGrowWithEventCount(){
        tx.executeWithoutResult(s->{Movie m=db.get(Movie.class,db.get(Showtime.class,show).movieId);for(int n=0;n<28;n++){Showtime extra=new Showtime();extra.roomId=room;extra.movieId=m.id;extra.startsAt=LocalDate.of(2026,12,7).plusDays(n/4).atTime(8+(n%4)*3,0).atZone(vn.cineprime.admin.ReportService.ZONE).toInstant();extra.endsAt=extra.startsAt.plusSeconds(6000);db.save(extra);}});
        var stats=entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();stats.setStatisticsEnabled(true);stats.clear();try{var week=batches.week(cinema,room,LocalDate.of(2026,12,7));assertEquals(28,((List<?>)week.get("events")).size());assertTrue(stats.getPrepareStatementCount()<=5,"Week must load events without N+1");}finally{stats.setStatisticsEnabled(false);}
    }
    @Test void manualMultipleMoviesAndMixedExcelShareOneAtomicBatch(){
        Movie second=(Movie)admin.save(5L,"movies",null,movieFields(null));var a=batchLine("manual","2026-12-07","10:00");var b=new vn.cineprime.admin.ShowtimeBatch.Line("excel",2,second.id.toString(),room.toString(),"2026-12-08","11:55","Phụ đề","2D","0");
        assertTrue(batches.preview(new vn.cineprime.admin.ShowtimeBatch.Input(cinema,"MANUAL","DRAFT",List.of(a,b))).canCommit());
        var input=new vn.cineprime.admin.ShowtimeBatch.Input(cinema,"MIXED","DRAFT",List.of(a,b));String key=UUID.randomUUID().toString();var result=commitBatch(input,key);assertEquals(2,result.created().size());assertEquals(result,commitBatch(input,key));
        var read=batches.week(cinema,room,LocalDate.of(2026,12,7));assertTrue(read.toString().contains("Phim có thể loại"));assertTrue(read.toString().contains("Phim kiểm thử tiếng Việt"));
    }
    @Test void weeklyMatrixLoadsAllRoomsAndUnscheduledMoviesWithoutHidingOccupancy(){
        Long secondRoom=tx.execute(s->{Room r=new Room();r.cinemaId=cinema;r.name="Phòng thứ hai";return db.save(r).id;});
        var first=batchLine("room-one","2026-12-07","09:00");var second=new vn.cineprime.admin.ShowtimeBatch.Line("room-two",null,first.movieId(),secondRoom.toString(),first.date(),first.time(),"Phụ đề","2D","0");
        commitBatch(batchInput("DRAFT",first,second),UUID.randomUUID().toString());Movie unscheduled=(Movie)admin.save(user1,"movies",null,movieFields(null));
        var all=batches.week(cinema,null,LocalDate.of(2026,12,7));assertEquals(2,((List<?>)all.get("events")).size());assertEquals(1,((List<?>)batches.week(cinema,secondRoom,LocalDate.of(2026,12,7)).get("events")).size());assertTrue(all.get("movies").toString().contains(unscheduled.title));assertTrue(all.get("movies").toString().contains("duration"));
    }
    @Test void newBatchesCannotSpanWeeksButSundayMayFinishOnMonday(){
        var multi=batchInput("DRAFT",batchLine("before","2026-12-06","22:25"),batchLine("after","2026-12-07","09:00"));var preview=batches.preview(multi);assertFalse(preview.canCommit());assertTrue(preview.rows().stream().allMatch(r->r.errors().stream().anyMatch(e->e.code().equals("MULTIPLE_WEEKS"))));assertThrows(vn.cineprime.admin.ShowtimeBatch.Failure.class,()->commitBatch(multi,UUID.randomUUID().toString()));assertEquals(1,db.count(Showtime.class,""));
        var sunday=batchInput("DRAFT",batchLine("sunday","2026-12-13","23:30"));assertTrue(batches.preview(sunday).canCommit());assertEquals("2026-12-14T01:25",batches.preview(sunday).rows().getFirst().cleanupUntil());assertEquals(1,commitBatch(sunday,UUID.randomUUID().toString()).created().size());
    }
    @Test void historicalMultiweekReceiptStillReplaysBeforeNewWeekValidation()throws Exception{
        var input=batchInput("DRAFT",batchLine("old-one","2026-12-06","10:00"),batchLine("old-two","2026-12-14","10:00"));String key=UUID.randomUUID().toString();
        var created=tx.execute(s->{List<vn.cineprime.admin.ShowtimeBatch.Created> records=new ArrayList<>();for(var line:input.rows()){Showtime saved=new Showtime();saved.movieId=Long.valueOf(line.movieId());saved.roomId=room;saved.startsAt=LocalDateTime.parse(line.date()+"T"+line.time()).atZone(vn.cineprime.admin.ReportService.ZONE).toInstant();saved.endsAt=saved.startsAt.plusSeconds(6000);db.save(saved);records.add(new vn.cineprime.admin.ShowtimeBatch.Created(line.rowKey(),saved.id,line.date()));}return records;});
        var result=new vn.cineprime.admin.ShowtimeBatch.Result(key,cinema,"DRAFT",created,clock.instant().toString());String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(input).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        new org.springframework.jdbc.core.JdbcTemplate(source).update("INSERT INTO showtime_batch_results(actor_id,cinema_id,request_key,payload_hash,result_json,committed_at) VALUES (?,?,?,?,?,?)",user1,cinema,key,hash,json.writeValueAsString(result),java.sql.Timestamp.from(clock.instant()));
        assertFalse(batches.preview(input).canCommit());assertEquals(result,batches.commit(user1,new vn.cineprime.admin.ShowtimeBatch.Commit(input,"old-fingerprint",key)));assertEquals(3,db.count(Showtime.class,""));
    }
    @Test void excelV3WeeklyEndpointsOnlyCreateRegisteredHours()throws Exception{
        HttpClient c=client();login(c,"admin@example.test");String token=csrf(get(c,"/admin/showtimes/new?cinemaId="+cinema).body());var url=URI.create("http://localhost:"+port+"/api/admin/showtime-batches/export.xlsx?cinemaId="+cinema+"&weekStart=2026-11-30");var response=c.send(HttpRequest.newBuilder(url).GET().build(),HttpResponse.BodyHandlers.ofByteArray());assertEquals(200,response.statusCode());
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(response.body()));var out=new java.io.ByteArrayOutputStream()){
            assertEquals(1,book.getSheet(vn.cineprime.admin.ShowtimeExcel.REFERENCE).getLastRowNum());var row=book.getSheet(vn.cineprime.admin.ShowtimeExcel.REGISTRATION).getRow(1);row.getCell(0).setCellValue("M"+db.get(Showtime.class,show).movieId);row.getCell(1).setCellValue("R"+room);row.getCell(4).setCellValue("11:55; 15:30");row.getCell(8).setCellValue("23:30");book.write(out);
            var parsed=showtimeExcel.readWorkbook(cinema,"DRAFT",new org.springframework.mock.web.MockMultipartFile("file","tuần.xlsx","",out.toByteArray()),LocalDate.of(2026,11,30));assertEquals(3,parsed.input().rows().size());assertEquals(3,parsed.excel().cells().size());assertTrue(batches.preview(parsed.input()).canCommit());assertEquals(3,commitBatch(parsed.input(),UUID.randomUUID().toString()).created().size());assertEquals(4,db.count(Showtime.class,""));assertEquals(5,db.count(Inventory.class,""));
            assertThrows(IllegalArgumentException.class,()->showtimeExcel.readWorkbook(cinema,"DRAFT",new org.springframework.mock.web.MockMultipartFile("file","tuần.xlsx","",out.toByteArray()),LocalDate.of(2026,12,7)));
        }
        assertEquals(200,get(c,"/api/admin/showtime-batches/template.xlsx?cinemaId="+cinema+"&weekStart=2026-11-30").statusCode());assertEquals(400,get(c,"/api/admin/showtime-batches/export.xlsx?cinemaId="+cinema+"&weekStart=2026-12-01").statusCode());assertEquals(200,get(c,"/api/admin/showtime-batches/calendar?cinemaId="+cinema+"&weekStart=2026-11-30").statusCode());
    }
    @Test void batchOpenRejectsTicketPriceOverflow(){var line=batchLine("huge","2026-12-02","10:00");line=new vn.cineprime.admin.ShowtimeBatch.Line(line.rowKey(),null,line.movieId(),line.roomId(),line.date(),line.time(),line.language(),line.format(),Long.toString(Long.MAX_VALUE));var preview=batches.preview(batchInput("OPEN",line));assertFalse(preview.canCommit());assertTrue(preview.rows().getFirst().errors().stream().anyMatch(e->e.message().contains("giới hạn số tiền")));}
    @Test void batchEditableInvalidCellsAndForeignRoomAreReportedTogether(){
        assertThrows(vn.cineprime.admin.ShowtimeBatch.Failure.class,()->batches.preview(new vn.cineprime.admin.ShowtimeBatch.Input(cinema,"MANUAL ","DRAFT",List.of(batchLine("x","2026-12-02","10:00")))));
        assertThrows(vn.cineprime.admin.ShowtimeBatch.Failure.class,()->batches.preview(batchInput("OPEN ",batchLine("x","2026-12-02","10:00"))));
        Long foreign=tx.execute(s->{Room r=new Room();r.cinemaId=db.list(Cinema.class,"where e.id<>?1",cinema).getFirst().id;r.name="Phòng rạp khác";return db.save(r).id;});
        var bad=new vn.cineprime.admin.ShowtimeBatch.Line("bad",7,"không phải mã",foreign.toString(),"2026-02-30","24:00","Phụ đề","2D","1.5");var preview=batches.preview(batchInput("DRAFT",bad));assertFalse(preview.canCommit());assertEquals(7,preview.rows().getFirst().input().sourceRow());assertEquals(Set.of("movieId","roomId","date","time","surcharge"),new HashSet<>(preview.rows().getFirst().errors().stream().map(vn.cineprime.admin.ShowtimeBatch.Problem::field).toList()));assertEquals(1,db.count(Showtime.class,""));
    }
    @Test void batchExcelHttpImportAndAccountLockAreEnforced()throws Exception{
        HttpClient c=client();login(c,"admin@example.test");var page=get(c,"/admin/showtimes/new?cinemaId="+cinema);String token=csrf(page.body());
        byte[] bytes=showtimeExcel.export(cinema,LocalDate.of(2026,12,1),LocalDate.of(2026,12,1),false);try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes));var out=new java.io.ByteArrayOutputStream()){copyExcelInput(book.getSheet(vn.cineprime.admin.ShowtimeExcel.REFERENCE).getRow(1),book.getSheet(vn.cineprime.admin.ShowtimeExcel.REGISTRATION).getRow(1));book.write(out);bytes=out.toByteArray();}String boundary="CinePrimeTestBoundary";var payload=new java.io.ByteArrayOutputStream();payload.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"cinemaId\"\r\n\r\n"+cinema+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"lich.xlsx\"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));payload.write(bytes);payload.write(("\r\n--"+boundary+"--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var response=c.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/admin/showtime-batches/import-preview")).header("Content-Type","multipart/form-data; boundary="+boundary).header("X-CSRF-TOKEN",token).POST(HttpRequest.BodyPublishers.ofByteArray(payload.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,response.statusCode());assertTrue(response.body().contains("EXISTING_CONFLICT"));assertEquals(2,json.readTree(response.body()).get("excel").get("templateVersion").asInt());assertEquals(vn.cineprime.admin.ShowtimeExcel.REGISTRATION,json.readTree(response.body()).get("excel").get("sheetName").asText());
        var template=get(c,"/api/admin/showtime-batches/template.xlsx?cinemaId="+cinema);assertEquals(200,template.statusCode());assertTrue(template.headers().firstValue("Content-Type").orElse("").contains("spreadsheetml"));
        tx.executeWithoutResult(s->db.first(User.class,"where e.email='admin@example.test'").orElseThrow().enabled=false);assertEquals(401,get(c,"/api/admin/showtime-batches/results/"+UUID.randomUUID()).statusCode());
    }
    @Test void batchMaximum500OpenShowsWith600SeatsUsesBoundedInventoryWrites()throws Exception{
        List<Long> roomIds=new ArrayList<>(List.of(room));
        tx.executeWithoutResult(s->{
            for(int n=1;n<10;n++){Room r=new Room();r.cinemaId=cinema;r.name="Phòng tải "+n;db.save(r);roomIds.add(r.id);}
            for(Long id:roomIds)for(int row=0;row<20;row++)for(int position=1;position<=30;position++){
                if(id.equals(room)&&row==0&&position<=5)continue;Seat seat=new Seat();seat.roomId=id;seat.rowLabel=String.valueOf((char)('A'+row));seat.label=seat.rowLabel+position;seat.position=position;db.save(seat);
            }
            for(String type:List.of("STANDARD","COUPLE")){Price p=new Price();p.cinemaId=cinema;p.dayType="WEEKEND";p.seatType=type;p.amount=85000;db.save(p);}
        });
        List<vn.cineprime.admin.ShowtimeBatch.Line> lines=new ArrayList<>();String movie=db.get(Showtime.class,show).movieId.toString();
        for(int n=0;n<500;n++){int day=n/80,slot=(n%80)/10;lines.add(new vn.cineprime.admin.ShowtimeBatch.Line("load-"+n,null,movie,roomIds.get(n%10).toString(),LocalDate.of(2026,12,7).plusDays(day).toString(),LocalTime.of(8,0).plusMinutes(slot*115L).toString(),"Phụ đề","2D","0"));}
        var input=new vn.cineprime.admin.ShowtimeBatch.Input(cinema,"MANUAL","OPEN",lines);long started=System.nanoTime();var result=commitBatch(input,UUID.randomUUID().toString());long elapsed=(System.nanoTime()-started)/1_000_000;assertEquals(500,result.created().size());assertEquals(300005,db.count(Inventory.class,""));java.nio.file.Files.writeString(java.nio.file.Path.of("target/batch-load.json"),"{\"shows\":500,\"seatsPerRoom\":600,\"createdInventory\":300000,\"previewAndCommitMs\":"+elapsed+"}");
        lines.add(batchLine("over-limit","2026-12-20","10:00"));assertThrows(vn.cineprime.admin.ShowtimeBatch.Failure.class,()->batches.preview(input));
    }
    @Autowired vn.cineprime.admin.ShowtimeExcel showtimeExcel;
    @Test void excelV2HundredReferenceShowsOnlyCreateThreeNewRegistrations()throws Exception{
        Long movie=db.get(Showtime.class,show).movieId;Instant original=db.get(Showtime.class,show).startsAt;
        tx.executeWithoutResult(s->{for(int n=0;n<99;n++){Showtime extra=new Showtime();extra.movieId=movie;extra.roomId=room;extra.status="DRAFT";extra.startsAt=LocalDate.of(2026,12,2).plusDays(n/4).atTime(8+(n%4)*3,0).atZone(vn.cineprime.admin.ReportService.ZONE).toInstant();extra.endsAt=extra.startsAt.plusSeconds(6000);db.save(extra);}});
        byte[] exported=showtimeExcel.export(cinema,LocalDate.of(2026,12,1),LocalDate.of(2026,12,31),false);
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(exported));var out=new java.io.ByteArrayOutputStream()){
            var reference=book.getSheet(vn.cineprime.admin.ShowtimeExcel.REFERENCE);assertEquals(100,reference.getLastRowNum());var registration=book.getSheet(vn.cineprime.admin.ShowtimeExcel.REGISTRATION);
            for(int n=1;n<=3;n++){var row=registration.getRow(n);copyExcelInput(reference.getRow(1),row);row.getCell(2).setCellValue(LocalDate.of(2026,12,27+n).atStartOfDay());row.getCell(3).setCellValue((11*60+55)/1440d);}
            reference.getRow(1).getCell(2).setCellValue("01/01/2000");book.write(out);
            var parsed=showtimeExcel.readWorkbook(cinema,"DRAFT",new org.springframework.mock.web.MockMultipartFile("file","thêm-suất.xlsx","",out.toByteArray()));assertEquals(3,parsed.input().rows().size());assertTrue(batches.preview(parsed.input()).canCommit());assertEquals(100,db.count(Showtime.class,""));assertEquals(3,commitBatch(parsed.input(),UUID.randomUUID().toString()).created().size());assertEquals(103,db.count(Showtime.class,""));assertEquals(original,db.get(Showtime.class,show).startsAt);assertEquals(5,db.count(Inventory.class,""));
        }
    }
    @Test void excelV2UntouchedExportHasClearHttpErrorAndDoesNotWrite()throws Exception{
        HttpClient c=client();login(c,"admin@example.test");String token=csrf(get(c,"/admin/showtimes/new?cinemaId="+cinema).body());byte[] bytes=showtimeExcel.export(cinema,LocalDate.of(2026,12,1),LocalDate.of(2026,12,1),false);
        String boundary="EmptyExcelV2";var payload=new java.io.ByteArrayOutputStream();payload.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"cinemaId\"\r\n\r\n"+cinema+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"unchanged.xlsx\"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));payload.write(bytes);payload.write(("\r\n--"+boundary+"--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var response=c.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/admin/showtime-batches/import-preview")).header("Content-Type","multipart/form-data; boundary="+boundary).header("X-CSRF-TOKEN",token).POST(HttpRequest.BodyPublishers.ofByteArray(payload.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());assertEquals(422,response.statusCode());assertEquals("EMPTY_REGISTRATION",json.readTree(response.body()).get("code").asText());assertTrue(json.readTree(response.body()).get("message").asText().contains(vn.cineprime.admin.ShowtimeExcel.REGISTRATION));assertEquals(1,db.count(Showtime.class,""));assertEquals(5,db.count(Inventory.class,""));
    }
    void copyExcelInput(org.apache.poi.ss.usermodel.Row source,org.apache.poi.ss.usermodel.Row target){for(int col=0;col<7;col++){var from=source.getCell(col);var to=target.getCell(col);if(to==null)to=target.createCell(col);switch(from.getCellType()){case NUMERIC->to.setCellValue(from.getNumericCellValue());case STRING->to.setCellValue(from.getStringCellValue());default->to.setBlank();}}}
    vn.cineprime.admin.ShowtimeBatch.Line batchLine(String key,String day,String time){return new vn.cineprime.admin.ShowtimeBatch.Line(key,null,db.get(Showtime.class,show).movieId.toString(),room.toString(),day,time,"Phụ đề","2D","0");}
    vn.cineprime.admin.ShowtimeBatch.Input batchInput(String status,vn.cineprime.admin.ShowtimeBatch.Line... rows){return new vn.cineprime.admin.ShowtimeBatch.Input(cinema,"MANUAL",status,List.of(rows));}
    vn.cineprime.admin.ShowtimeBatch.Result commitBatch(vn.cineprime.admin.ShowtimeBatch.Input input,String key){var preview=batches.preview(input);return batches.commit(user1,new vn.cineprime.admin.ShowtimeBatch.Commit(input,preview.fingerprint(),key));}
    Showtime createShow(Map<String,String> fields){
        var start=LocalDateTime.parse(fields.get("startsAt"));Long cinemaId=fields.containsKey("cinemaId")?Long.valueOf(fields.get("cinemaId")):db.get(Room.class,Long.valueOf(fields.get("roomId"))).cinemaId;
        var line=new vn.cineprime.admin.ShowtimeBatch.Line(UUID.randomUUID().toString(),null,fields.get("movieId"),fields.get("roomId"),start.toLocalDate().toString(),start.toLocalTime().toString(),fields.getOrDefault("language","Phụ đề"),fields.getOrDefault("format","2D"),fields.getOrDefault("surcharge","0"));
        var input=new vn.cineprime.admin.ShowtimeBatch.Input(cinemaId,"MANUAL",fields.getOrDefault("status","DRAFT"),List.of(line));return db.get(Showtime.class,commitBatch(input,UUID.randomUUID().toString()).created().getFirst().showtimeId());
    }
    @Test void batchDraftCreatesAllDaysAndPersistentReplay(){
        var input=batchInput("DRAFT",batchLine("a","2026-12-02","10:00"),batchLine("b","2026-12-03","14:00"));String key=UUID.randomUUID().toString();var result=commitBatch(input,key);assertEquals(2,result.created().size());assertEquals(3,db.count(Showtime.class,""));assertEquals(5,db.count(Inventory.class,""));assertEquals(result,commitBatch(input,key));assertEquals(result,batches.result(user1,key).orElseThrow());assertTrue(batches.result(user2,key).isEmpty());
        var changed=batchInput("DRAFT",batchLine("a","2026-12-04","10:00"));assertEquals("IDEMPOTENCY_KEY_REUSED",assertThrows(vn.cineprime.admin.ShowtimeBatch.Failure.class,()->commitBatch(changed,key)).code);
    }
    @Test void batchOpenCreatesInventoryAndUsesSharedOpeningRules(){var input=batchInput("OPEN",batchLine("a","2026-12-02","10:00"),batchLine("b","2026-12-02","11:55"));var result=commitBatch(input,UUID.randomUUID().toString());assertEquals(2,result.created().size());assertEquals(15,db.count(Inventory.class,""));assertEquals("OPEN",db.get(Showtime.class,result.created().getFirst().showtimeId()).status);}
    @Test void batchInternalOverlapMarksBothRowsAndWritesNothing(){var input=batchInput("DRAFT",batchLine("a","2026-12-02","10:00"),batchLine("b","2026-12-02","11:54"));var preview=batches.preview(input);assertEquals(2,preview.invalid());assertTrue(preview.rows().stream().allMatch(r->r.errors().stream().anyMatch(p->p.code().equals("BATCH_CONFLICT"))));assertThrows(vn.cineprime.admin.ShowtimeBatch.Failure.class,()->commitBatch(input,UUID.randomUUID().toString()));assertEquals(1,db.count(Showtime.class,""));}
    @Test void batchRejectsPastAndExistingDraftButCancelledDoesNotBlock(){
        assertFalse(batches.preview(batchInput("DRAFT",batchLine("past","2026-12-01","16:59"))).canCommit());
        var input=batchInput("DRAFT",batchLine("a","2026-12-02","23:30"));var saved=commitBatch(input,UUID.randomUUID().toString());assertFalse(batches.preview(batchInput("DRAFT",batchLine("b","2026-12-03","01:24"))).canCommit());assertTrue(batches.preview(batchInput("DRAFT",batchLine("b","2026-12-03","01:25"))).canCommit());
        admin.remove(user1,"showtimes",saved.created().getFirst().showtimeId());assertTrue(batches.preview(input).canCommit());
    }
    @Test void batchDraftWarnsMissingPricesWhileOpenIsBlocked(){tx.executeWithoutResult(s->db.list(Price.class,"").forEach(db::delete));var line=batchLine("a","2026-12-02","10:00");var draft=batches.preview(batchInput("DRAFT",line));assertTrue(draft.canCommit());assertFalse(draft.rows().getFirst().warnings().isEmpty());assertFalse(batches.preview(batchInput("OPEN",line)).canCommit());}
    @Test void batchStaleMovieDurationRequiresNewPreview(){var input=batchInput("DRAFT",batchLine("a","2026-12-02","10:00"));var preview=batches.preview(input);tx.executeWithoutResult(s->db.get(Movie.class,db.get(Showtime.class,show).movieId).duration=110);var failure=assertThrows(vn.cineprime.admin.ShowtimeBatch.Failure.class,()->batches.commit(user1,new vn.cineprime.admin.ShowtimeBatch.Commit(input,preview.fingerprint(),UUID.randomUUID().toString())));assertEquals("PREVIEW_STALE",failure.code);assertEquals(1,db.count(Showtime.class,""));}
    @Test void competingBatchAndExistingShowEditHaveOnlyOneWinner()throws Exception{
        var input=batchInput("DRAFT",batchLine("a","2026-12-02","10:00"));var preview=batches.preview(input);var edit=new HashMap<>(showFields(db.get(Showtime.class,show)));edit.put("startsAt","2026-12-02T10:00");
        var results=race(()->batches.commit(user1,new vn.cineprime.admin.ShowtimeBatch.Commit(input,preview.fingerprint(),UUID.randomUUID().toString())),()->admin.save(user2,"showtimes",show,edit));assertEquals(1,results.stream().filter(Boolean::booleanValue).count());assertEquals(1,db.count(Showtime.class,"where e.startsAt=?1",LocalDateTime.parse("2026-12-02T10:00").atZone(vn.cineprime.admin.ReportService.ZONE).toInstant()));
    }
    @Test void duplicateBatchRequestsConcurrentlyReturnSameResult()throws Exception{
        var input=batchInput("OPEN",batchLine("a","2026-12-02","10:00"));var preview=batches.preview(input);var command=new vn.cineprime.admin.ShowtimeBatch.Commit(input,preview.fingerprint(),UUID.randomUUID().toString());var results=race(()->batches.commit(user1,command),()->batches.commit(user1,command));assertEquals(List.of(true,true),results);assertEquals(2,db.count(Showtime.class,""));assertEquals(10,db.count(Inventory.class,""));
    }
    @Test void batchInventoryFailureRollsBackEveryShowAndClaim()throws Exception{
        try(var connection=source.getConnection();var statement=connection.createStatement()){
            statement.execute("ALTER TABLE inventory ADD CONSTRAINT batch_test_inventory_failure CHECK(showtime_id<="+(show+1)+")");
            try{var input=batchInput("OPEN",batchLine("a","2026-12-02","10:00"),batchLine("b","2026-12-03","10:00"));String key=UUID.randomUUID().toString();assertThrows(RuntimeException.class,()->commitBatch(input,key));assertEquals(1,db.count(Showtime.class,""));assertEquals(5,db.count(Inventory.class,""));assertTrue(batches.result(user1,key).isEmpty());}finally{statement.execute("ALTER TABLE inventory DROP CHECK batch_test_inventory_failure");}
        }
    }
    @Test void batchCalendarContainsPreviousDayCleanupAndNextDayShows(){var result=commitBatch(batchInput("DRAFT",batchLine("a","2026-12-02","23:30"),batchLine("b","2026-12-04","01:30")),UUID.randomUUID().toString());var calendar=batches.calendar(cinema,LocalDate.of(2026,12,3));assertEquals(2,((List<?>)calendar.get("events")).size());}
    @Test void batchExcelRoundTripDefaultsTypedDatesAndStableCodes()throws Exception{
        byte[] template=showtimeExcel.export(cinema,null,null,true);try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(template));var bytes=new java.io.ByteArrayOutputStream()){
            assertEquals(4,book.getNumberOfSheets());var sheet=book.getSheet(vn.cineprime.admin.ShowtimeExcel.REGISTRATION);var row=sheet.getRow(1);row.getCell(0).setCellValue("M"+db.get(Showtime.class,show).movieId+" — Tên cũ");row.getCell(1).setCellValue("R"+room);row.getCell(2).setCellValue(LocalDate.of(2026,12,2).atStartOfDay());row.getCell(3).setCellValue(10d/24);book.write(bytes);
            var input=showtimeExcel.read(cinema,"DRAFT",new org.springframework.mock.web.MockMultipartFile("file","lich.xlsx","application/octet-stream",bytes.toByteArray()));var preview=batches.preview(input);assertTrue(preview.canCommit());assertEquals(2,input.rows().getFirst().sourceRow());assertEquals("2026-12-02",input.rows().getFirst().date());assertEquals("10:00",input.rows().getFirst().time());assertEquals("Phụ đề",preview.rows().getFirst().input().language());assertEquals("0",preview.rows().getFirst().input().surcharge());commitBatch(input,UUID.randomUUID().toString());
        }
        var exported=showtimeExcel.export(cinema,LocalDate.of(2026,12,2),LocalDate.of(2026,12,2),false);assertEquals("EMPTY_REGISTRATION",assertThrows(vn.cineprime.admin.ShowtimeBatch.Failure.class,()->showtimeExcel.read(cinema,"DRAFT",new org.springframework.mock.web.MockMultipartFile("file","copy.xlsx","",exported))).code);
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(exported));var bytes=new java.io.ByteArrayOutputStream()){var row=book.getSheet(vn.cineprime.admin.ShowtimeExcel.REGISTRATION).getRow(1);copyExcelInput(book.getSheet(vn.cineprime.admin.ShowtimeExcel.REFERENCE).getRow(1),row);book.write(bytes);assertFalse(batches.preview(showtimeExcel.read(cinema,"DRAFT",new org.springframework.mock.web.MockMultipartFile("file","copy.xlsx","",bytes.toByteArray()))).canCommit());bytes.reset();row.getCell(2).setCellValue("03/12/2026");book.write(bytes);var copied=showtimeExcel.read(cinema,"DRAFT",new org.springframework.mock.web.MockMultipartFile("file","copy.xlsx","",bytes.toByteArray()));assertEquals(1,commitBatch(copied,UUID.randomUUID().toString()).created().size());}
    }
    @Test void batchExcelFormulaAndWrongCinemaAreRejected()throws Exception{
        byte[] template=showtimeExcel.export(cinema,null,null,true);try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(template));var bytes=new java.io.ByteArrayOutputStream()){
            var row=book.getSheet(vn.cineprime.admin.ShowtimeExcel.REGISTRATION).getRow(1);row.getCell(0).setCellValue("M"+db.get(Showtime.class,show).movieId);row.getCell(1).setCellValue("R"+room);row.getCell(2).setCellValue("02/12/2026");row.getCell(3).setCellValue("10:00");row.getCell(4).setCellFormula("1+1");book.write(bytes);var file=new org.springframework.mock.web.MockMultipartFile("file","test.xlsx","",bytes.toByteArray());assertFalse(batches.preview(showtimeExcel.read(cinema,"DRAFT",file)).canCommit());assertThrows(IllegalArgumentException.class,()->showtimeExcel.read(cinema+1,"DRAFT",file));
        }
        assertThrows(IllegalArgumentException.class,()->showtimeExcel.read(cinema,"DRAFT",new org.springframework.mock.web.MockMultipartFile("file","test.xls","",new byte[1])));assertThrows(IllegalArgumentException.class,()->showtimeExcel.export(cinema,LocalDate.of(2026,12,1),LocalDate.of(2027,1,1),false));
    }
    @Test void batchApiEnforcesAdminCsrfAndRender()throws Exception{
        HttpClient customer=client();login(customer,"one@example.test");assertEquals(403,get(customer,"/api/admin/showtime-batches/calendar?cinemaId="+cinema+"&date=2026-12-02").statusCode());HttpClient administrator=client();login(administrator,"admin@example.test");var page=get(administrator,"/admin/showtimes/new?cinemaId="+cinema+"&date=2026-12-02");assertEquals(200,page.statusCode());assertTrue(org.springframework.web.util.HtmlUtils.htmlUnescape(page.body()).contains("Thêm suất chiếu"));assertTrue(page.body().contains("/js/showtime-batch.js"));
        var mapper=new tools.jackson.databind.ObjectMapper();var input=batchInput("DRAFT",batchLine("a","2026-12-02","10:00"));String payload=mapper.writeValueAsString(input);var url=URI.create("http://localhost:"+port+"/api/admin/showtime-batches/preview");assertEquals(403,administrator.send(HttpRequest.newBuilder(url).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(payload)).build(),HttpResponse.BodyHandlers.ofString()).statusCode());var response=administrator.send(HttpRequest.newBuilder(url).header("Content-Type","application/json").header("X-CSRF-TOKEN",csrf(page.body())).POST(HttpRequest.BodyPublishers.ofString(payload)).build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,response.statusCode());assertTrue(response.body().contains("\"canCommit\":true"));assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
    }
    static class MutableClock extends Clock {
        final AtomicReference<Instant> value=new AtomicReference<>(Instant.parse("2026-12-01T10:00:00Z"));
        public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId z){return this;} public Instant instant(){return value.get();}
        void advance(long seconds){value.updateAndGet(t->t.plusSeconds(seconds));}
    }
    @TestConfiguration static class TimeConfig {@Bean @Primary MutableClock testClock(){return new MutableClock();}}
    @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",()->System.getenv("TEST_DB_URL"));r.add("spring.datasource.username",()->System.getenv().getOrDefault("TEST_DB_USER","cineprime"));r.add("spring.datasource.password",()->System.getenv().getOrDefault("TEST_DB_PASSWORD",""));}
    @Autowired Store db;@Autowired BookingService booking;@Autowired CheckInService checkin;@Autowired AdminService admin;@Autowired AccountService accounts;@Autowired TicketFiles files;@Autowired MomoService momo;@Autowired MutableClock clock;@Autowired DataSource source;@Autowired PlatformTransactionManager tm;
    @LocalServerPort int port;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
    @Autowired vn.cineprime.web.Catalog catalog;
    @Autowired vn.cineprime.booking.BookingController bookingPages;
    @Autowired vn.cineprime.admin.ReportService reports;
    @Autowired vn.cineprime.staff.StaffReadService staffReads;
    @Autowired vn.cineprime.admin.AdminPresentation presentation;
    @Autowired vn.cineprime.admin.AdminReads adminReads;
    @Autowired vn.cineprime.admin.OperationsService operationReads;
    @Autowired vn.cineprime.admin.ShowtimeEditor showtimeEditor;@Autowired vn.cineprime.media.MediaService media;
    Long user1,user2,staff,otherStaff,show,room,seat1,seat2,seat3,pair1,pair2,voucher,cinema,genre;
    @Autowired vn.cineprime.admin.RoomSetup setup;@Autowired vn.cineprime.admin.MovieGenres movieGenres;@Autowired vn.cineprime.admin.RoomPrices roomPrices;
    @Autowired vn.cineprime.auth.SecurityConfig.RateLimit rates;
    TransactionTemplate tx;
    @BeforeEach void fixture()throws Exception {
        // Each test models an independent traffic window; the real HTTP limiter stays enabled.
        ((Map<?,?>)org.springframework.test.util.ReflectionTestUtils.getField(rates,"windows")).clear();
        clock.value.set(Instant.parse("2026-12-01T10:00:00Z"));tx=new TransactionTemplate(tm);
        try(var c=source.getConnection();var statement=c.createStatement()){
            assertTrue(c.getCatalog().equals("cineprime_test"),"Refuse to reset a non-test schema");
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            try{for(String table:List.of("showtime_batch_results","mail_jobs","audit_logs","contents","notifications","subscriptions","reviews","favorites","point_ledger","tickets","payments","voucher_uses","order_products","order_seats","inventory","orders","vouchers","products","prices","showtimes","seats","rooms","credits","movie_genres","movies","people","genres","reset_tokens","users","cinemas"))statement.execute("TRUNCATE TABLE "+table);}finally{statement.execute("SET FOREIGN_KEY_CHECKS=1");}
        }
        tx.executeWithoutResult(status->{
            Cinema c=new Cinema();c.name="Rạp kiểm thử";c.city="TP. Hồ Chí Minh";db.save(c);cinema=c.id;
            Cinema other=new Cinema();other.name="Khác rạp";other.city="Hà Nội";db.save(other);
            user1=accounts.register("one@example.test","Khách Một","CinePrimeTest2026!").id;user2=accounts.register("two@example.test","Khách Hai","CinePrimeTest2026!").id;
            User st=accounts.register("staff@example.test","Nhân viên","CinePrimeTest2026!");st.role="STAFF";st.cinemaId=cinema;staff=st.id;
            User ost=accounts.register("other@example.test","Khác rạp","CinePrimeTest2026!");ost.role="STAFF";ost.cinemaId=other.id;otherStaff=ost.id;
            User administrator=accounts.register("admin@example.test","Quản trị","CinePrimeTest2026!");administrator.role="ADMIN";
            Movie m=new Movie();m.title="Phim kiểm thử tiếng Việt";m.slug="phim-kiem-thu";m.duration=100;m.status="NOW_SHOWING";m.poster="/images/poster-1.svg";m.description="Một câu chuyện để kiểm tra hiển thị tiếng Việt.";m.releaseDate=LocalDate.of(2026,11,1);m.endDate=LocalDate.of(2027,1,31);db.save(m);
            Genre g=new Genre();g.name="Phiêu lưu";db.save(g);genre=g.id;MovieGenre mg=new MovieGenre();mg.movieId=m.id;mg.genreId=g.id;db.save(mg);
            Room r=new Room();r.cinemaId=c.id;r.name="Phòng 1";db.save(r);room=r.id;
            Showtime s=new Showtime();s.movieId=m.id;s.roomId=r.id;s.startsAt=clock.instant().plusSeconds(3600);s.endsAt=s.startsAt.plusSeconds(6000);s.status="OPEN";db.save(s);show=s.id;
            List<Long> ids=new ArrayList<>();for(int i=1;i<=5;i++){Seat seat=new Seat();seat.roomId=r.id;seat.rowLabel="A";seat.label="A"+i;seat.position=i;if(i>=4){seat.type="COUPLE";seat.coupleGroup="A-pair";}db.save(seat);ids.add(seat.id);Inventory inv=new Inventory();inv.showtimeId=show;inv.seatId=seat.id;db.save(inv);}seat1=ids.get(0);seat2=ids.get(1);seat3=ids.get(2);pair1=ids.get(3);pair2=ids.get(4);
            for(String type:List.of("STANDARD","COUPLE")){Price p=new Price();p.cinemaId=c.id;p.seatType=type;p.dayType="WEEKDAY";p.amount=type.equals("COUPLE")?95000:85000;db.save(p);}
            Voucher v=new Voucher();v.code="LAST";v.kind="PERCENT";v.value=10;v.quota=1;v.perUser=1;v.startsAt=clock.instant().minusSeconds(60);v.endsAt=clock.instant().plusSeconds(86400);db.save(v);voucher=v.id;
        });
    }
    List<Boolean> race(Callable<?> a,Callable<?> b)throws Exception{
        CountDownLatch gate=new CountDownLatch(1);try(ExecutorService pool=Executors.newFixedThreadPool(2)){
            List<Future<Boolean>> results=new ArrayList<>();for(Callable<?> task:List.of(a,b))results.add(pool.submit(()->{gate.await();try{task.call();return true;}catch(IllegalArgumentException|org.springframework.dao.DataAccessException e){return false;}}));gate.countDown();return List.of(results.get(0).get(15,TimeUnit.SECONDS),results.get(1).get(15,TimeUnit.SECONDS));
        }
    }
    Payment payment(Order order){return booking.preparePayment(order.userId,order.id);}
    void paid(Order order){Payment p=payment(order);booking.paymentResult(p.externalOrderId,p.amount,"tx-"+p.id,0);}
    @Test void competingHoldsHaveExactlyOneWinner()throws Exception{
        var results=race(()->booking.hold(user1,show,List.of(seat1)),()->booking.hold(user2,show,List.of(seat1)));assertEquals(1,results.stream().filter(Boolean::booleanValue).count());assertEquals(1,db.list(Order.class,"").size());
    }
    @Test void multiSeatFailureDoesNotLeavePartialHold(){booking.hold(user1,show,List.of(seat2));assertThrows(IllegalArgumentException.class,()->booking.hold(user2,show,List.of(seat1,seat2)));assertEquals("AVAILABLE",db.first(Inventory.class,"where e.seatId=?1",seat1).orElseThrow().status);assertEquals(1,db.list(Order.class,"").size());}
    @Test void expiryAtExactlyFiveMinutesAndReleaseWithoutBrowser(){Order o=booking.hold(user1,show,List.of(seat1));assertEquals(clock.instant().plusSeconds(300),o.expiresAt);clock.advance(299);booking.expire(o.id);assertEquals("PENDING_PAYMENT",db.get(Order.class,o.id).status);clock.advance(1);booking.expire(o.id);assertEquals("EXPIRED",db.get(Order.class,o.id).status);assertDoesNotThrow(()->booking.hold(user2,show,List.of(seat1)));}
    @Test void coupleMustBeBoughtAsPair(){assertThrows(IllegalArgumentException.class,()->booking.hold(user1,show,List.of(pair1)));Order o=booking.hold(user1,show,List.of(pair1,pair2));assertEquals(190000,o.total);paid(o);assertEquals(2,db.list(Ticket.class,"").size());}
    @Test void duplicateAndReorderedPaymentDoesNotDuplicateFulfillment(){Order o=booking.hold(user1,show,List.of(seat1,seat2));Payment p=payment(o);booking.paymentResult(p.externalOrderId,p.amount,"tx",0);booking.paymentResult(p.externalOrderId,p.amount,"tx",0);booking.paymentResult(p.externalOrderId,p.amount,"tx",1006);assertEquals("PAID",db.get(Order.class,o.id).status);assertEquals("SUCCEEDED",db.get(Payment.class,p.id).status);assertEquals(2,db.list(Ticket.class,"").size());assertEquals(1,db.list(Point.class,"").size());assertEquals(1,db.list(MailJob.class,"").size());}
    @Test void latePaymentNeverReclaimsSeats(){Order o=booking.hold(user1,show,List.of(seat1));Payment p=payment(o);clock.advance(300);Order next=booking.hold(user2,show,List.of(seat1));booking.paymentResult(p.externalOrderId,p.amount,"late-tx",0);assertTrue(db.get(Payment.class,p.id).reconciliation);assertEquals("EXPIRED",db.get(Order.class,o.id).status);assertEquals(next.id,db.first(Inventory.class,"where e.seatId=?1",seat1).orElseThrow().orderId);assertEquals(0,db.list(Ticket.class,"").size());}
    @Test void wrongAmountDoesNotFulfill(){Order o=booking.hold(user1,show,List.of(seat1));Payment p=payment(o);assertThrows(IllegalArgumentException.class,()->booking.paymentResult(p.externalOrderId,p.amount+1,"wrong",0));assertEquals(0,db.list(Ticket.class,"").size());}
    @Test void finalVoucherQuotaIsAtomicAndReleasedOnce()throws Exception{
        Order a=booking.hold(user1,show,List.of(seat1));Order b=booking.hold(user2,show,List.of(seat2));var result=race(()->{booking.updateCart(user1,a.id,Map.of(),"LAST");return null;},()->{booking.updateCart(user2,b.id,Map.of(),"LAST");return null;});assertEquals(1,result.stream().filter(Boolean::booleanValue).count());Order winner=result.get(0)?a:b;Order loser=result.get(0)?b:a;booking.cancel(winner.userId,winner.id);booking.updateCart(loser.userId,loser.id,Map.of(),"LAST");paid(loser);assertEquals(1,db.list(VoucherUse.class,"where e.status='USED'").size());assertEquals(76500,db.get(Order.class,loser.id).total);
    }
    @Test void checkInRaceAndWrongCinema()throws Exception{Order o=booking.hold(user1,show,List.of(seat1));paid(o);Ticket t=db.list(Ticket.class,"").getFirst();assertThrows(org.springframework.security.access.AccessDeniedException.class,()->checkin.checkIn(otherStaff,t.id,show));var result=race(()->checkin.checkIn(staff,t.id,show),()->checkin.checkIn(staff,t.id,show));assertEquals(1,result.stream().filter(Boolean::booleanValue).count());assertEquals("USED",db.get(Ticket.class,t.id).status);}
    @Test void checkInRejectsWrongShowAndTime(){Order o=booking.hold(user1,show,List.of(seat1));paid(o);Ticket t=db.list(Ticket.class,"").getFirst();assertThrows(IllegalArgumentException.class,()->checkin.checkIn(staff,t.id,show+99));clock.advance(5401);assertThrows(IllegalArgumentException.class,()->checkin.checkIn(staff,t.id,show));}
    @Test void priceSnapshotSurvivesPriceEdits(){Order o=booking.hold(user1,show,List.of(seat1));tx.executeWithoutResult(status->{db.list(Price.class,"where e.seatType='STANDARD'").getFirst().amount=999000;});assertEquals(85000,db.get(Order.class,o.id).total);assertEquals(85000,db.list(OrderSeat.class,"where e.orderId=?1",o.id).getFirst().price);}
    @Test void scheduleOverlapIncludesCleaningBuffer(){Showtime existing=db.get(Showtime.class,show);Map<String,String> fields=new HashMap<>();fields.put("movieId",existing.movieId.toString());fields.put("roomId",room.toString());fields.put("startsAt",db.local(existing.endsAt.plusSeconds(899)));fields.put("language","Phụ đề");fields.put("format","2D");fields.put("status","DRAFT");fields.put("surcharge","0");assertThrows(IllegalArgumentException.class,()->createShow(fields));fields.put("startsAt",db.local(existing.endsAt.plusSeconds(900)));assertDoesNotThrow(()->createShow(fields));}
    @Test void activeHoldProtectsSchedule(){Order o=booking.hold(user1,show,List.of(seat1));assertThrows(IllegalArgumentException.class,()->admin.remove(user1,"showtimes",show));assertThrows(IllegalArgumentException.class,()->admin.generateSeats(user1,room,2,4));assertEquals("PENDING_PAYMENT",db.get(Order.class,o.id).status);}
    @Test void invalidSignatureAndWrongRequestAreRejected(){Order o=booking.hold(user1,show,List.of(seat1));Payment p=payment(o);Map<String,Object> body=ipn(p);body.put("signature","forged");assertThrows(IllegalArgumentException.class,()->momo.ipn(body));body.put("signature",signature(body));momo.ipn(body);assertEquals("PAID",db.get(Order.class,o.id).status);}
    Map<String,Object> ipn(Payment p){Map<String,Object> b=new LinkedHashMap<>();b.put("partnerCode","TEST_PARTNER");b.put("amount",p.amount);b.put("extraData","");b.put("message","Success");b.put("orderId",p.externalOrderId);b.put("orderInfo","CinePrime");b.put("orderType","momo_wallet");b.put("payType","qr");b.put("requestId",p.requestId);b.put("responseTime",123L);b.put("resultCode",0);b.put("transId",12345L);return b;}
    String signature(Map<String,Object> b){StringJoiner j=new StringJoiner("&");j.add("accessKey=TEST_ACCESS");for(String k:List.of("amount","extraData","message","orderId","orderInfo","orderType","partnerCode","payType","requestId","responseTime","resultCode","transId"))j.add(k+"="+b.get(k));return MomoService.sign("TEST_SECRET",j.toString());}
    @Test void pdfContainsVietnameseAndQrCanDecode()throws Exception{Order o=booking.hold(user1,show,List.of(seat1));paid(o);Ticket t=db.list(Ticket.class,"").getFirst();try(var pdf=Loader.loadPDF(files.pdf(t))){String text=new PDFTextStripper().getText(pdf);assertTrue(text.contains("Phim kiểm thử tiếng Việt"));assertTrue(text.contains("Ghế: A1"));}var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(files.qr(t)));var bitmap=new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(new com.google.zxing.client.j2se.BufferedImageLuminanceSource(image)));assertEquals("CP1:"+t.token,new com.google.zxing.MultiFormatReader().decode(bitmap).getText());}
    HttpClient client(){return HttpClient.newBuilder().cookieHandler(new CookieManager()).followRedirects(HttpClient.Redirect.NEVER).build();}
    HttpResponse<String> get(HttpClient c,String path)throws Exception{return c.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString());}
    HttpResponse<String> getFollowing(HttpClient c,String path)throws Exception{var response=get(c,path);for(int n=0;n<3&&response.statusCode()==302;n++){String url=response.headers().firstValue("location").orElseThrow();var uri=URI.create(url);response=get(c,uri.getRawPath()+(uri.getRawQuery()==null?"":"?"+uri.getRawQuery()));}return response;}
    String csrf(String html){Matcher matcher=Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(html);assertTrue(matcher.find(),html.substring(0,Math.min(600,html.length())));return matcher.group(1);}
    HttpResponse<String> post(HttpClient c,String path,String data)throws Exception{return c.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(data)).build(),HttpResponse.BodyHandlers.ofString());}
    void login(HttpClient c,String email)throws Exception{String token=csrf(get(c,"/login").body());assertEquals(302,post(c,"/login","email="+URLEncoder.encode(email,java.nio.charset.StandardCharsets.UTF_8)+"&password=CinePrimeTest2026!&_csrf="+URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8)).statusCode());}
    @Test void dashboardQueriesDoNotGrowPerSeatOrShowtime()throws Exception {
        assertFalse(catalog.shows(null,null,null).isEmpty());
        Order order=booking.hold(user1,show,List.of(seat1));paid(order);
        tx.executeWithoutResult(status->{
            db.get(Seat.class,seat3).active=false;
            Showtime original=db.get(Showtime.class,show);
            for(int n=0;n<30;n++){
                Showtime s=new Showtime();s.movieId=original.movieId;s.roomId=room;s.startsAt=original.startsAt;s.endsAt=original.endsAt;s.status="OPEN";db.save(s);
                for(Long seat:List.of(seat1,seat2,seat3,pair1,pair2)){Inventory i=new Inventory();i.showtimeId=s.id;i.seatId=seat;db.save(i);}
            }
        });
        HttpClient c=client();login(c,"admin@example.test");
        var stats=entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);stats.clear();
        try {
            var response=get(c,"/admin?period=day&date=2026-12-01");
            assertEquals(200,response.statusCode());
            assertTrue(response.body().contains("1 / 4 (25%)"),"Sold tickets and active-seat capacity must stay accurate");
            assertTrue(stats.getPrepareStatementCount()<50,"Dashboard must use grouped queries, actual: "+stats.getPrepareStatementCount());
        } finally {stats.setStatisticsEnabled(false);}
    }
    @Test void seatPollingUsesBoundedQueriesAndReadsCurrentHolds(){
        tx.executeWithoutResult(status->{for(int n=0;n<100;n++){
            Seat s=new Seat();s.roomId=room;s.label="B"+n;s.rowLabel="B";s.position=n;db.save(s);
            Inventory i=new Inventory();i.showtimeId=show;i.seatId=s.id;db.save(i);
        }});
        var stats=entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();stats.setStatisticsEnabled(true);stats.clear();
        try {
            assertEquals(105,((List<?>)bookingPages.seats(show).get("seats")).size());
            assertTrue(stats.getPrepareStatementCount()<12,"Polling must not query each seat: "+stats.getPrepareStatementCount());
        } finally {stats.setStatisticsEnabled(false);}
        booking.hold(user1,show,List.of(seat1));
        assertTrue(((List<?>)bookingPages.seats(show).get("seats")).stream().map(v->(Map<?,?>)v).anyMatch(v->seat1.equals(v.get("id"))&&"HELD".equals(v.get("state"))));
        clock.advance(300);
        assertTrue(((List<?>)bookingPages.seats(show).get("seats")).stream().map(v->(Map<?,?>)v).anyMatch(v->seat1.equals(v.get("id"))&&"AVAILABLE".equals(v.get("state"))));
    }
    @Test void publicAndAuthenticatedTemplatesRenderAndRolesAreEnforced()throws Exception{
        HttpClient guest=client();for(String path:List.of("/","/movies","/movies/phim-kiem-thu","/showtimes","/cinemas","/cinemas/"+cinema,"/login","/register","/forgot-password","/reset-password?token=x","/offers","/articles")){var r=get(guest,path);assertEquals(200,r.statusCode(),path+": "+r.body());assertTrue(r.body().contains("CinePrime"),path);}
        assertEquals(403,post(guest,"/register","email=x").statusCode());
        HttpClient customer=client();login(customer,"one@example.test");assertEquals(200,get(customer,"/account").statusCode());assertEquals(200,get(customer,"/booking/"+show).statusCode());assertEquals(403,get(customer,"/admin").statusCode());assertEquals(403,get(customer,"/staff").statusCode());
        Order o=booking.hold(user1,show,List.of(seat1));assertEquals(200,get(customer,"/orders/"+o.id).statusCode());HttpClient other=client();login(other,"two@example.test");assertEquals(403,get(other,"/orders/"+o.id).statusCode());
        HttpClient administrator=client();login(administrator,"admin@example.test");for(String path:List.of("/admin","/admin/orders","/admin/orders/"+o.id,"/admin/upload","/admin/audit")){assertEquals(200,get(administrator,path).statusCode(),path);}for(String key:AdminService.RESOURCES.keySet()){assertEquals(200,getFollowing(administrator,"/admin/manage/"+key).statusCode(),key);if(!key.equals("reviews"))assertEquals(200,get(administrator,"/admin/manage/"+key+"/edit"+(key.equals("showtimes")?"?id="+show:"")).statusCode(),key+" edit");}
        HttpClient st=client();login(st,"staff@example.test");assertEquals(200,get(st,"/staff?date=2026-12-01&showtimeId="+show).statusCode());
    }
    @Test void lockedAccountInvalidatesExistingSession()throws Exception{HttpClient c=client();login(c,"one@example.test");assertEquals(200,get(c,"/account").statusCode());tx.executeWithoutResult(status->db.get(User.class,user1).enabled=false);assertEquals(302,get(c,"/account").statusCode());}
    @Test void zeroTotalVoucherIsRealFreeOrderNotGatewaySimulation(){tx.executeWithoutResult(status->{Voucher v=db.get(Voucher.class,voucher);v.value=100;});Order o=booking.hold(user1,show,List.of(seat1));booking.updateCart(user1,o.id,Map.of(),"LAST");assertEquals(0,db.get(Order.class,o.id).total);booking.completeFree(user1,o.id);booking.completeFree(user1,o.id);assertEquals("PAID",db.get(Order.class,o.id).status);assertEquals("VOUCHER",db.list(Payment.class,"").getFirst().provider);assertEquals(1,db.list(Ticket.class,"").size());assertEquals(0,db.list(Point.class,"").getFirst().points);}
    @Test void resetTokenIsSingleUseAndExpires(){accounts.forgot("one@example.test");MailJob mail=db.list(MailJob.class,"").getFirst();String raw=mail.body.substring(mail.body.indexOf("token=")+6);accounts.reset(raw,"NewCinePrime2026!");assertThrows(IllegalArgumentException.class,()->accounts.reset(raw,"AnotherPassword2026!"));accounts.forgot("one@example.test");MailJob next=db.list(MailJob.class,"order by e.id desc").getFirst();String expired=next.body.substring(next.body.indexOf("token=")+6);clock.advance(1800);assertThrows(IllegalArgumentException.class,()->accounts.reset(expired,"AnotherPassword2026!"));}
    @Test void checkoutChangesAndReadsNeverExtendHold(){Order o=booking.hold(user1,show,List.of(seat1));Instant expiry=o.expiresAt;clock.advance(60);booking.updateCart(user1,o.id,Map.of(),"LAST");assertEquals(expiry,booking.owned(user1,o.id).expiresAt);payment(o);assertThrows(IllegalArgumentException.class,()->booking.updateCart(user1,o.id,Map.of(),""));assertEquals(expiry,db.get(Order.class,o.id).expiresAt);}
    @Test void cancelledOrderWithLateSuccessNeedsReconciliation(){Order o=booking.hold(user1,show,List.of(seat1));Payment p=payment(o);booking.cancel(user1,o.id);booking.paymentResult(p.externalOrderId,p.amount,"cancel-tx",0);assertEquals("CANCELLED",db.get(Order.class,o.id).status);assertTrue(db.get(Payment.class,p.id).reconciliation);assertTrue(db.list(Ticket.class,"").isEmpty());}
    @Test void newAdminViewsRender()throws Exception{HttpClient c=client();login(c,"admin@example.test");for(String path:List.of("/admin/schedule?date=2026-12-01","/admin/layout/"+room,"/admin/voucher-usage/"+voucher,"/admin/operations")){assertEquals(200,getFollowing(c,path).statusCode(),path);}}
    @Test void reportSnapshotMatchesChartGroupingAndCsvWithDiscountFreeAndLatePayment(){
        Long product=tx.execute(status->{Product p=new Product();p.name="Combo kiểm thử";p.kind="COMBO";p.price=43000;db.save(p);return p.id;});
        Order first=booking.hold(user1,show,List.of(pair1,pair2));booking.updateCart(user1,first.id,Map.of(product,2),"LAST");paid(first);long expected=db.get(Order.class,first.id).total;
        tx.executeWithoutResult(status->{Payment retry=new Payment();retry.orderId=first.id;retry.externalOrderId="failed-retry";retry.requestId="failed-request";retry.amount=expected;retry.status="FAILED";retry.createdAt=clock.instant();db.save(retry);Voucher free=new Voucher();free.code="FREE";free.kind="PERCENT";free.value=100;free.startsAt=clock.instant().minusSeconds(1);free.endsAt=clock.instant().plusSeconds(9999);db.save(free);});
        Order free=booking.hold(user2,show,List.of(seat1));booking.updateCart(user2,free.id,Map.of(),"FREE");booking.completeFree(user2,free.id);
        Order late=booking.hold(user1,show,List.of(seat3));Payment latePayment=payment(late);clock.advance(300);booking.expire(late.id);booking.paymentResult(latePayment.externalOrderId,latePayment.amount,"late",0);clock.advance(1);
        var snapshot=reports.create("day",LocalDate.of(2026,12,1),cinema);assertEquals(expected,snapshot.model().get("revenueValue"));assertEquals(2,snapshot.model().get("orderCount"));assertEquals(3L,snapshot.model().get("ticketCount"));
        assertEquals(expected,((List<?>)snapshot.model().get("chart")).stream().mapToLong(v->((Number)((Map<?,?>)v).get("value")).longValue()).sum());
        assertEquals(expected,((List<?>)snapshot.model().get("topMovies")).stream().mapToLong(v->((Number)((Map<?,?>)v).get("value")).longValue()).sum());
        String csv=new String(snapshot.csv(),java.nio.charset.StandardCharsets.UTF_8);assertEquals(3,csv.lines().count());assertTrue(csv.contains("\""+first.code+"\""));assertTrue(csv.contains("\""+free.code+"\""));assertFalse(csv.contains(late.code));assertTrue(csv.contains(",\""+expected+"\"\r\n"));
        tx.executeWithoutResult(status->{db.get(Cinema.class,cinema).active=false;db.get(Cinema.class,cinema).name="Đổi tên rạp";db.get(Movie.class,db.get(Showtime.class,show).movieId).title="Tên mới";});
        assertEquals(expected,reports.create("day",LocalDate.of(2026,12,1),cinema).model().get("revenueValue"));assertTrue(new String(snapshot.csv(),java.nio.charset.StandardCharsets.UTF_8).contains("Phim kiểm thử tiếng Việt"));
        Long other=db.get(User.class,otherStaff).cinemaId;assertEquals(0L,reports.create("day",LocalDate.of(2026,12,1),other).model().get("revenueValue"));
    }
    @Test void reportUsesVietnamMidnightAndExclusiveCapturedCutoff(){
        Order before=booking.hold(user1,show,List.of(seat1));paid(before);Order midnight=booking.hold(user2,show,List.of(seat2));paid(midnight);Order cutoff=booking.hold(user1,show,List.of(seat3));paid(cutoff);
        tx.executeWithoutResult(status->{db.get(Order.class,before.id).paidAt=Instant.parse("2026-11-30T16:59:59Z");db.get(Order.class,midnight.id).paidAt=Instant.parse("2026-11-30T17:00:00Z");});
        var report=reports.create("day",LocalDate.of(2026,12,1),null);assertEquals(1,report.model().get("orderCount"));assertEquals(85000L,report.model().get("revenueValue"));clock.advance(1);assertEquals(2,reports.create("day",LocalDate.of(2026,12,1),null).model().get("orderCount"));
        assertEquals(0L,reports.create("day",LocalDate.of(2027,1,1),null).model().get("revenueValue"));
    }
    @Test void snapshotDownloadIsStableSessionBoundAndExpires()throws Exception{
        Order first=booking.hold(user1,show,List.of(seat1));paid(first);clock.advance(1);HttpClient c=client();login(c,"admin@example.test");String html=get(c,"/admin?date=2026-12-01").body();Matcher link=Pattern.compile("/admin/revenue/([a-f0-9-]+)\\.csv").matcher(html);assertTrue(link.find());String url=link.group();String old=get(c,url).body();
        Order second=booking.hold(user2,show,List.of(seat2));paid(second);clock.advance(1);assertEquals(old,get(c,url).body());assertFalse(old.contains(second.code));
        HttpClient another=client();login(another,"admin@example.test");assertEquals(400,get(another,url).statusCode());
        get(c,"/admin?date=2026-12-01");get(c,"/admin?date=2026-12-01");assertEquals(400,get(c,url).statusCode());
        var session=new org.springframework.mock.web.MockHttpSession();var snapshot=reports.create("day",LocalDate.of(2026,12,1),null);reports.remember(session,snapshot);clock.advance(900);assertThrows(IllegalArgumentException.class,()->reports.recall(session,snapshot.id()));
    }
    @Test void staffReadEligibilityAndCountsStayAuthoritative(){
        Order order=booking.hold(user1,show,List.of(seat1,seat2));paid(order);var lookup=staffReads.lookup(staff,order.code,show);List<?> found=(List<?>)lookup.get("tickets");assertEquals(2,found.size());assertTrue((Boolean)((Map<?,?>)found.getFirst()).get("canCheckIn"));
        Long first=(Long)((Map<?,?>)found.getFirst()).get("id");checkin.checkIn(staff,first,show);var state=staffReads.tickets(staff,show,"","",0);assertEquals(1L,((Map<?,?>)state.get("summary")).get("used"));assertEquals("50.0%",((Map<?,?>)state.get("summary")).get("rate"));
        assertEquals("USED",staffReads.one(staff,first,show).get("reasonCode"));assertThrows(org.springframework.security.access.AccessDeniedException.class,()->staffReads.tickets(otherStaff,show,"","",0));assertThrows(IllegalArgumentException.class,()->staffReads.page(staff,LocalDate.of(2026,12,2),show));
        Long second=(Long)((Map<?,?>)found.get(1)).get("id");clock.advance(5400);assertTrue((Boolean)staffReads.one(staff,second,show).get("canCheckIn"));clock.advance(1);assertEquals("OUTSIDE_WINDOW",staffReads.one(staff,second,show).get("reasonCode"));
        tx.executeWithoutResult(status->{db.get(Ticket.class,second).status="VOID";db.get(Order.class,order.id).code="CP-ELIGIBILITY";for(Ticket ticket:db.list(Ticket.class,"where e.orderId=?1",order.id))ticket.code="T-ELIGIBILITY-"+ticket.id;});assertEquals(1L,((Map<?,?>)staffReads.tickets(staff,show,"","",0).get("summary")).get("total"));assertEquals(1L,staffReads.tickets(staff,show,"A1","",0).get("total"));
        var report=reports.create("day",LocalDate.of(2026,12,1),cinema);assertEquals(170000L,report.model().get("revenueValue"));assertTrue(report.model().get("occupancy").toString().contains("1 / 5 (20%)"));
    }
    @Test void formsKeepInputAndCanonicalTimeAndRejectCrossRoleReads()throws Exception{
        HttpClient c=client();login(c,"admin@example.test");String priceForm=get(c,"/admin/manage/prices/edit").body();assertTrue(priceForm.contains("data-money=\"true\""));assertTrue(get(c,"/admin/manage/movies/edit").body().contains("data-video=\"true\""));String token=csrf(priceForm);String data="cinemaId="+cinema+"&seatType=STANDARD&dayType=WEEKEND&fromMinute=17%3A00&toMinute=24%3A00&amount=123457&_csrf="+URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8);assertEquals(302,post(c,"/admin/manage/prices/save",data).statusCode());Price price=db.first(Price.class,"where e.dayType='WEEKEND'").orElseThrow();assertEquals(1020,price.fromMinute);assertEquals(1440,price.toMinute);assertEquals(123457L,price.amount);
        var invalid=post(c,"/admin/manage/prices/save",data.replace("amount=123457","amount=-17"));assertEquals(400,invalid.statusCode());assertTrue(invalid.body().contains("value=\"-17\""));assertTrue(invalid.body().contains("value=\"17:00\""));assertTrue(invalid.body().contains("Chưa thể lưu"));
        HttpClient st=client();login(st,"staff@example.test");assertEquals(403,get(st,"/admin/operations").statusCode());assertEquals(403,post(st,"/api/staff/lookup","{}").statusCode());assertFalse(get(c,"/admin").body().contains("header-search"));assertFalse(get(st,"/staff").body().contains("footer-grid"));
    }
    @Test void adminSearchAndPaginationUseRelatedLabelsAndKeepFilters()throws Exception{
        tx.executeWithoutResult(status->{for(int i=0;i<27;i++){Genre genre=new Genre();genre.name="Nhóm thử "+i;db.save(genre);}MovieGenre link=new MovieGenre();link.movieId=db.get(Showtime.class,show).movieId;link.genreId=db.first(Genre.class,"where e.name like ?1","Nhóm thử %").orElseThrow().id;db.save(link);});
        HttpClient c=client();login(c,"admin@example.test");var first=get(c,"/admin/manage/genres?q=Nh%C3%B3m&state=true");assertEquals(200,first.statusCode());assertTrue(first.body().contains("27 kết quả"));assertTrue(first.body().contains("data-page=\"1\""));assertTrue(first.body().contains("returnTo="));
        var second=get(c,"/admin/manage/genres?q=Nh%C3%B3m&state=true&page=1");assertEquals(200,second.statusCode());assertTrue(second.body().contains("Trang 2"));
        assertEquals(200,get(c,"/admin/manage/movie-genres?q=Phim").statusCode());assertEquals(200,get(c,"/admin/manage/showtimes?q=Phim&cinemaId="+cinema).statusCode());assertEquals(200,get(c,"/admin/manage/users?state=STAFF&activity=true&cinemaId="+cinema).statusCode());
        var prices=get(c,"/admin/manage/prices?state=COUPLE&cinemaId="+cinema);assertEquals(200,prices.statusCode());assertTrue(prices.body().contains("95.000"));assertFalse(prices.body().contains("85.000"));assertEquals(200,get(c,"/admin/manage/seats?state=COUPLE&activity=true&cinemaId="+cinema+"&roomId="+room).statusCode());
    }
    HttpResponse<String> staffJson(HttpClient c,String path,String json,String token)throws Exception{return c.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json").header("X-CSRF-TOKEN",token).POST(HttpRequest.BodyPublishers.ofString(json)).build(),HttpResponse.BodyHandlers.ofString());}
    @Test void staffApiRequiresContextAndPreservesOneEffectAfterLostResponse()throws Exception{
        Order o=booking.hold(user1,show,List.of(seat1,seat2));paid(o);List<Ticket> tickets=db.list(Ticket.class,"order by e.id");HttpClient c=client();login(c,"staff@example.test");String token=csrf(get(c,"/staff?date=2026-12-01&showtimeId="+show).body());
        var lookup=staffJson(c,"/api/staff/lookup","{\"code\":\""+o.code+"\",\"showtimeId\":"+show+"}",token);assertEquals(200,lookup.statusCode());assertTrue(lookup.body().contains("\"canCheckIn\":true"));
        String json="{\"ticketId\":"+tickets.getFirst().id+",\"showtimeId\":"+show+"}";assertEquals(200,staffJson(c,"/api/staff/check-in",json,token).statusCode());
        var reread=get(c,"/api/staff/tickets/"+tickets.getFirst().id+"?showtimeId="+show);assertTrue(reread.body().contains("\"reasonCode\":\"USED\""));var repeated=staffJson(c,"/api/staff/check-in",json,token);assertEquals(400,repeated.statusCode());assertTrue(repeated.body().contains("\"code\":\"USED\""));assertEquals(1,db.list(Audit.class,"where e.action='CHECK_IN'").size());
        HttpClient other=client();login(other,"other@example.test");assertEquals(403,get(other,"/api/staff/showtimes/"+show+"/tickets").statusCode());
        tx.executeWithoutResult(status->db.get(User.class,staff).enabled=false);var expired=get(c,"/api/staff/showtimes/"+show+"/tickets");assertEquals(401,expired.statusCode());assertTrue(expired.body().contains("SESSION_EXPIRED"));
    }
    @Test void lockedShowFormPreservesSecondsAndProtectedValues()throws Exception{
        tx.executeWithoutResult(status->{Showtime s=db.get(Showtime.class,show);s.startsAt=s.startsAt.plusSeconds(37);s.endsAt=s.endsAt.plusSeconds(37);});Order o=booking.hold(user1,show,List.of(seat1));paid(o);Showtime s=db.get(Showtime.class,show);HttpClient c=client();login(c,"admin@example.test");String html=get(c,"/admin/manage/showtimes/edit?id="+show).body();assertTrue(html.contains("value=\"2026-12-01T18:00:37\""));assertTrue(html.contains("readonly"));String token=csrf(html);String values="id="+show+"&movieId="+s.movieId+"&roomId="+room+"&startsAt=2026-12-01T18%3A00%3A37&language=Test&format=2D&status=OPEN&surcharge=0&_csrf="+URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8);assertEquals(302,post(c,"/admin/manage/showtimes/save",values).statusCode());assertEquals(s.startsAt,db.get(Showtime.class,show).startsAt);assertEquals("Test",db.get(Showtime.class,show).language);
    }
    @Test void inlineUploadChecksCsrfAndServesRuntimeImage()throws Exception{
        HttpClient c=client();login(c,"admin@example.test");String token=csrf(get(c,"/admin/manage/movies/edit").body());var buffer=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",buffer);String boundary="CinePrimeImageBoundary";var body=new java.io.ByteArrayOutputStream();body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"video\"\r\n\r\nfalse\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"fixture.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));body.write(buffer.toByteArray());body.write(("\r\n--"+boundary+"--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/admin/media")).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));assertEquals(403,c.send(request.build(),HttpResponse.BodyHandlers.ofString()).statusCode());var response=c.send(request.header("X-CSRF-TOKEN",token).build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,response.statusCode());Matcher url=Pattern.compile("/media/([a-f0-9-]{36}\\.png)").matcher(response.body());assertTrue(url.find());assertEquals(200,get(c,url.group()).statusCode());java.nio.file.Path root=java.nio.file.Path.of("uploads").toAbsolutePath().normalize(),file=root.resolve(url.group(1)).normalize();assertTrue(file.startsWith(root));java.nio.file.Files.deleteIfExists(file);
    }
    @Test void loginResumesSelectedBooking()throws Exception{HttpClient c=client();assertEquals(302,get(c,"/booking/"+show).statusCode());String token=csrf(get(c,"/login").body());var result=post(c,"/login","email=one%40example.test&password=CinePrimeTest2026!&_csrf="+URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8));assertEquals(302,result.statusCode());assertTrue(result.headers().firstValue("location").orElseThrow().contains("/booking/"+show));assertEquals(200,get(c,"/booking/"+show).statusCode());}
    @Test void adminPagesHandleZeroOneFiveSixElevenAndDatabaseFilters(){
        for(int count:List.of(0,1,5,6,11)){
            tx.executeWithoutResult(status->{db.entityManager().createQuery("delete from "+db.entityName(MovieGenre.class)).executeUpdate();db.entityManager().createQuery("delete from "+db.entityName(Genre.class)).executeUpdate();for(int n=0;n<count;n++){Genre g=new Genre();g.name="Phân trang "+n;g.active=n%2==0;db.save(g);}});
            var first=presentation.list("genres","","","",null,null,0);assertEquals(Math.min(count,5),((List<?>)first.get("rows")).size());assertEquals((long)count,first.get("total"));assertEquals(count>5,first.get("hasMore"));
            var last=presentation.list("genres","","","",null,null,100);assertEquals(Math.max(0,(count-1)/5),last.get("page"));assertTrue(((List<?>)last.get("rows")).size()<=5);
            var filtered=presentation.list("genres","Phân trang","true","",null,null,0);assertEquals((count+1L)/2,filtered.get("total"));assertTrue(((List<?>)filtered.get("rows")).size()<=5);
        }
        assertEquals(2L,presentation.list("users","","CUSTOMER","",null,null,0).get("total"));assertEquals(2L,presentation.list("users","","STAFF","",null,null,0).get("total"));assertEquals(1L,presentation.list("users","","ADMIN","",null,null,0).get("total"));
        assertEquals(0L,presentation.list("seats","","","",cinema,null,0).get("total"));
    }
    @Test void scheduleFiltersBeforePagingAndUsesStoredMidnightEnd(){
        Long movie=db.get(Showtime.class,show).movieId;
        tx.executeWithoutResult(status->{
            for(int n=0;n<10;n++){Showtime s=new Showtime();s.movieId=movie;s.roomId=room;s.startsAt=clock.instant().plusSeconds(4000+n*10);s.endsAt=s.startsAt.plusSeconds(6000);s.status="OPEN";db.save(s);}
            Showtime midnight=new Showtime();midnight.movieId=movie;midnight.roomId=room;midnight.startsAt=Instant.parse("2026-12-01T16:30:00Z");midnight.endsAt=Instant.parse("2026-12-01T18:15:00Z");midnight.status="OPEN";db.save(midnight);db.get(Movie.class,movie).duration=240;
            Room duplicate=new Room();duplicate.cinemaId=db.get(User.class,otherStaff).cinemaId;duplicate.name="Phòng 1";db.save(duplicate);
        });
        var first=adminReads.schedule(LocalDate.of(2026,12,1),cinema,movie,"","OPEN",0);assertEquals(12L,first.get("total"));assertEquals(5,((List<?>)first.get("rows")).size());
        var second=adminReads.schedule(LocalDate.of(2026,12,1),cinema,movie,"","OPEN",1);assertEquals(5,((List<?>)second.get("rows")).size());assertEquals(1,second.get("page"));
        var last=adminReads.schedule(LocalDate.of(2026,12,1),cinema,movie,"","OPEN",2);assertTrue(last.get("rows").toString().contains("23:30–01:15 (+1 ngày)"));
        assertEquals(0L,adminReads.schedule(LocalDate.of(2026,12,1),db.get(User.class,otherStaff).cinemaId,movie,"","",0).get("total"));
        assertEquals(1,adminReads.movies(cinema,LocalDate.of(2026,12,1)).size());assertEquals(0,adminReads.movies(cinema,LocalDate.of(2026,12,2)).size());
        var list=presentation.list("showtimes","","OPEN","",cinema,null,movie,LocalDate.of(2026,12,1),"",2);assertEquals(2,((List<?>)list.get("rows")).size());assertTrue(list.get("rows").toString().contains("+1 ngày"));
    }
    Order readOrder(int n,Long showtime){
        Order o=new Order();o.code="READ-"+UUID.randomUUID();o.userId=user1;o.showtimeId=showtime;o.createdAt=clock.instant().minusSeconds(60+n);o.expiresAt=clock.instant().plusSeconds(300);o.status="PAID";o.paidAt=clock.instant().minusSeconds(1);o.total=n*1001;o.subtotal=o.total;o.movieTitle="Phim "+n;o.cinemaName="Rạp kiểm thử";o.roomName="Phòng 1";o.showStartsAt=db.get(Showtime.class,showtime).startsAt;return db.save(o);
    }
    @SuppressWarnings("unchecked") List<Map<String,Object>> rows(Map<String,Object> data){return (List<Map<String,Object>>)data.get("rows");}
    @Test void operationalCursorsKeepAllPagesStableWhenNewRowsArrive(){
        tx.executeWithoutResult(status->{for(int n=0;n<11;n++){Order o=readOrder(n,show);for(int attempt=0;attempt<2;attempt++){Payment p=new Payment();p.orderId=o.id;p.requestId=UUID.randomUUID().toString();p.externalOrderId=p.requestId;p.status="FAILED";p.reconciliation=attempt==0;p.amount=o.total;p.createdAt=clock.instant().minusSeconds(1);db.save(p);}}});
        var first=operationReads.page("latest",cinema,"");assertEquals(11L,first.get("total"));assertEquals(5,rows(first).size());assertTrue(rows(first).getFirst().get("detail").toString().contains("Khách Một"));
        String cursor=first.get("nextCursor").toString();tx.executeWithoutResult(status->readOrder(99,show));
        var second=operationReads.page("latest",cinema,cursor);assertEquals(11L,second.get("total"));Set<Object> ids=new HashSet<>();rows(first).forEach(r->assertTrue(ids.add(r.get("recordId"))));rows(second).forEach(r->assertTrue(ids.add(r.get("recordId"))));var third=operationReads.page("latest",cinema,second.get("nextCursor").toString());rows(third).forEach(r->assertTrue(ids.add(r.get("recordId"))));assertEquals(11,ids.size());assertEquals(false,third.get("hasMore"));assertEquals(12L,operationReads.page("latest",cinema,"").get("total"));
        for(String section:List.of("reconciliation","failures")){var page=operationReads.page(section,cinema,"");assertEquals(section.equals("failures")?22L:11L,page.get("total"));assertEquals(5,rows(page).size());assertEquals(5,rows(operationReads.page(section,cinema,page.get("nextCursor").toString())).size());}
        assertEquals(2,rows(adminReads.orderSection((Long)rows(first).getFirst().get("id"),"payments",0)).size());assertThrows(IllegalArgumentException.class,()->operationReads.page("latest",cinema,cursor+"x"));assertThrows(IllegalArgumentException.class,()->operationReads.page("failures",cinema,cursor));assertThrows(IllegalArgumentException.class,()->operationReads.page("latest",db.get(User.class,otherStaff).cinemaId,cursor));clock.advance(900);assertThrows(IllegalArgumentException.class,()->operationReads.page("latest",cinema,cursor));
    }
    @Test void managementReadsPageAtDatabaseAndKeepFullScopeCounts(){
        tx.executeWithoutResult(status->{for(int n=0;n<11;n++){Order o=readOrder(n,show);VoucherUse use=new VoucherUse();use.voucherId=voucher;use.orderId=o.id;use.userId=user1;use.status=n%2==0?"USED":"RESERVED";use.expiresAt=clock.instant().plusSeconds(300);db.save(use);db.audit(user1,"PAGING_READ",o.code);}});
        for(int page=0;page<3;page++){assertEquals(page==2?1:5,rows(adminReads.orders("Khách Một","PAID","",cinema,LocalDate.of(2026,12,1),page)).size());assertEquals(page==2?1:5,rows(adminReads.audit("PAGING_READ",page)).size());assertEquals(page==2?1:5,rows(adminReads.voucherUsage(voucher,"","",page)).size());}
        var usage=adminReads.voucherUsage(voucher,"","RESERVED",0);assertEquals(5L,usage.get("total"));assertEquals(6L,usage.get("used"));assertEquals(5L,usage.get("reserved"));assertEquals(0L,adminReads.orders("","PAID","FAILED",cinema,null,0).get("total"));
        var stats=entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();stats.setStatisticsEnabled(true);stats.clear();try{adminReads.orders("","","",cinema,null,1);assertTrue(stats.getPrepareStatementCount()<6,"Only count, page and page-payment queries");}finally{stats.setStatisticsEnabled(false);}
    }
    @Test void snapshotSectionPagesKeepAllFilmGroupsAndNeverReplaceCsvOrSnapshot()throws Exception{
        tx.executeWithoutResult(status->{for(int n=0;n<11;n++){Movie m=dated(new Movie());m.title="Nhóm doanh thu "+n;m.slug="doanh-thu-"+n;db.save(m);Showtime s=new Showtime();s.movieId=m.id;s.roomId=room;s.startsAt=clock.instant().plusSeconds(3600);s.endsAt=s.startsAt.plusSeconds(6000);s.status="OPEN";db.save(s);readOrder(n,s.id);}});
        HttpClient c=client();login(c,"admin@example.test");String html=get(c,"/admin?date=2026-12-01").body();Matcher matcher=Pattern.compile("/admin/revenue/([a-f0-9-]+)\\.csv").matcher(html);assertTrue(matcher.find());String id=matcher.group(1),csv=get(c,matcher.group()).body();
        for(String section:List.of("chart","topMovies","occupancy"))for(int page=0;page<3;page++){var response=get(c,"/api/admin/reports/"+id+"/"+section+"?page="+page);assertEquals(200,response.statusCode(),response.body());assertTrue(response.body().contains("\"snapshotId\":\""+id+"\""));assertTrue(response.body().contains("\"page\":"+page));}
        tx.executeWithoutResult(status->readOrder(99,show));assertEquals(csv,get(c,"/admin/revenue/"+id+".csv").body());
        var snapshot=reports.create("day",LocalDate.of(2026,12,1),cinema);long sum=0;for(int page=0;page<3;page++)for(var row:reports.section(snapshot,"topMovies",page).rows())sum+=((Number)row.get("value")).longValue();assertEquals(snapshot.model().get("revenueValue"),sum);
        HttpClient other=client();login(other,"admin@example.test");assertEquals(400,get(other,"/api/admin/reports/"+id+"/chart").statusCode());HttpClient staffClient=client();login(staffClient,"staff@example.test");assertEquals(403,get(staffClient,"/api/admin/reports/"+id+"/chart").statusCode());
        assertEquals(400,get(c,"/api/admin/reports/"+id+"/unknown").statusCode());clock.advance(900);assertEquals(400,get(c,"/api/admin/reports/"+id+"/chart").statusCode());
    }
    @Test void adminContextsAndTabsAreExplicitAndKeepReturnFilters()throws Exception{
        HttpClient c=client();login(c,"admin@example.test");var redirect=get(c,"/admin/manage/rooms");assertEquals(302,redirect.statusCode());assertTrue(redirect.headers().firstValue("location").orElseThrow().contains("cinemaId="+db.get(User.class,otherStaff).cinemaId));
        assertEquals(200,get(c,"/admin/manage/rooms?cinemaId="+cinema).statusCode());var remembered=get(c,"/admin/manage/prices");assertTrue(remembered.headers().firstValue("location").orElseThrow().contains("cinemaId="+cinema));
        assertEquals(302,get(c,"/admin/schedule?cinemaId="+cinema+"&movieId=99999&page=2").statusCode());assertEquals(302,get(c,"/admin/manage/seats?cinemaId="+cinema+"&roomId=99999").statusCode());
        var seats=get(c,"/admin/manage/seats?cinemaId="+cinema);assertEquals(200,seats.statusCode(),seats.body());assertTrue(org.springframework.web.util.HtmlUtils.htmlUnescape(seats.body()).contains("Chọn phòng trong rạp"),"Room-selection guidance");assertTrue(seats.body().contains("0 kết quả"),seats.body());
        String users=org.springframework.web.util.HtmlUtils.htmlUnescape(get(c,"/admin/manage/users").body());assertTrue(users.contains("account-tabs"),"Role tabs");assertTrue(users.contains("Khách Một"),"Customer name");assertFalse(users.contains("staff@example.test"),"Default tab must be Customer");var staffTab=get(c,"/admin/manage/users?state=STAFF");assertEquals(200,staffTab.statusCode(),staffTab.body());assertTrue(staffTab.body().contains("staff@example.test"),staffTab.body());
        Long administrator=db.first(User.class,"where e.role='ADMIN'").orElseThrow().id;
        String accountToken=csrf(get(c,"/admin/manage/users/edit?id="+administrator).body());
        String accountData="id="+administrator+"&email=admin%40example.test&name=Admin&role=CUSTOMER&enabled=true&_csrf="+URLEncoder.encode(accountToken,java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(400,post(c,"/admin/manage/users/save",accountData).statusCode(),"Cannot demote the final Admin");
        assertEquals(400,post(c,"/admin/manage/users/save",accountData.replace("role=CUSTOMER&enabled=true","role=ADMIN")).statusCode(),"Cannot disable the final Admin");
        assertEquals("ADMIN",db.get(User.class,administrator).role);assertTrue(db.get(User.class,administrator).enabled);assertEquals(200,get(c,"/admin/manage/users?state=ADMIN").statusCode());
        String path="/admin/manage/genres?q=Gi%E1%BB%AF&state=true&page=1";String form=get(c,"/admin/manage/genres/edit?returnTo="+URLEncoder.encode(path,java.nio.charset.StandardCharsets.UTF_8)).body();String token=csrf(form);var saved=post(c,"/admin/manage/genres/save","name=Gi%E1%BB%AF&active=true&returnTo="+URLEncoder.encode(path,java.nio.charset.StandardCharsets.UTF_8)+"&_csrf="+URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8));assertEquals(302,saved.statusCode());assertTrue(saved.headers().firstValue("location").orElseThrow().contains("state=true&page=1"));
    }
    HttpResponse<String> uploadImage(HttpClient c,String token,String kind,String mime,byte[] bytes)throws Exception{
        String boundary="MovieUploadBoundary";var body=new java.io.ByteArrayOutputStream();body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"kind\"\r\n\r\n"+kind+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"fixture\"\r\nContent-Type: "+mime+"\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));body.write(bytes);body.write(("\r\n--"+boundary+"--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/admin/media")).header("Content-Type","multipart/form-data; boundary="+boundary).header("X-CSRF-TOKEN",token).POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();return c.send(request,HttpResponse.BodyHandlers.ofString());
    }
    @Test void movieMediaPersistsThroughAdminAndCustomerAndAllFeaturedBanners()throws Exception{
        HttpClient c=client();login(c,"admin@example.test");String token=csrf(get(c,"/admin/manage/movies/edit").body());
        var uploaded=uploadImage(c,token,"poster","image/webp",MediaServiceTest.webp());assertEquals(200,uploaded.statusCode(),uploaded.body());Matcher path=Pattern.compile("/media/[a-f0-9-]{36}\\.webp").matcher(uploaded.body());assertTrue(path.find());String url=path.group();
        try{
            assertTrue(get(c,url).headers().firstValue("Content-Type").orElseThrow().contains("image/webp"));
            String data="genresPresent=true&genreIds="+genre+"&title=Phim+upload&slug=phim-upload&description=Test&duration=100&ageRating=P&releaseDate=2026-11-01&endDate=2027-01-31&active=true&featured=true&poster="+URLEncoder.encode(url,java.nio.charset.StandardCharsets.UTF_8)+"&banner="+URLEncoder.encode(url,java.nio.charset.StandardCharsets.UTF_8)+"&trailer="+URLEncoder.encode("https://youtu.be/M7lc1UVf-VE?si=test",java.nio.charset.StandardCharsets.UTF_8)+"&_csrf="+URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(302,post(c,"/admin/manage/movies/save",data).statusCode());Movie movie=db.first(Movie.class,"where e.slug='phim-upload'").orElseThrow();assertEquals(url,movie.poster);assertEquals(url,movie.banner);assertEquals("https://www.youtube-nocookie.com/embed/M7lc1UVf-VE",movie.trailer);
            assertTrue(get(c,"/admin/manage/movies/edit?id="+movie.id).body().contains(url));assertTrue(get(c,"/movies/phim-upload").body().contains(url));assertTrue(get(c,"/").body().contains("class=\"hero-banner\" src=\""+url+"\""));assertFalse(get(c,"/movies/phim-upload").body().contains("<iframe"),"Player must be loaded only on request");
            tx.executeWithoutResult(status->{for(int n=0;n<4;n++){Movie m=dated(new Movie());m.title="Banner "+n;m.slug="banner-"+n;m.featured=true;m.status="NOW_SHOWING";db.save(m);}});
            String home=get(c,"/").body();for(int n=0;n<4;n++)assertTrue(home.contains("/movies/banner-"+n));assertTrue(home.contains("/movies/phim-upload"));
            var invalid=post(c,"/admin/manage/movies/save",data.replace("slug=phim-upload","slug=phim-invalid").replace("poster="+URLEncoder.encode(url,java.nio.charset.StandardCharsets.UTF_8),"poster=https%3A%2F%2Fwww.youtube.com%2Fembed%2FM7lc1UVf-VE"));assertEquals(400,invalid.statusCode());assertTrue(invalid.body().contains("value=\"phim-invalid\""));
            assertEquals(400,uploadImage(c,token,"poster","video/mp4",new byte[]{0,0,0,20,'f','t','y','p','i','s','o','m'}).statusCode());
        }finally{java.nio.file.Files.deleteIfExists(media.read(url.substring(7)).getFile().toPath());}
    }
    @Test void hideMovieStopsNewBookingsButRetainsHeldPaidAndStaffTickets()throws Exception{
        Order held=booking.hold(user1,show,List.of(seat1));Order alreadyPaid=booking.hold(user2,show,List.of(seat2));paid(alreadyPaid);Long movie=db.get(Showtime.class,show).movieId;
        HttpClient c=client();login(c,"admin@example.test");String token=csrf(get(c,"/admin/manage/movies").body());var hidden=post(c,"/admin/manage/movies/"+movie+"/disable","returnTo=%2Fadmin%2Fmanage%2Fmovies&_csrf="+URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8));assertEquals(302,hidden.statusCode());assertFalse(db.get(Movie.class,movie).active);String list=org.springframework.web.util.HtmlUtils.htmlUnescape(get(c,"/admin/manage/movies").body());assertTrue(list.contains("Đã ẩn phim"));assertTrue(list.contains("Hiện lại"));
        assertEquals(400,get(c,"/movies/phim-kiem-thu").statusCode());assertEquals(400,get(c,"/booking/"+show).statusCode());assertEquals(400,get(c,"/api/showtimes/"+show+"/seats").statusCode());assertTrue(catalog.shows(null,null,null).isEmpty());assertThrows(IllegalArgumentException.class,()->booking.hold(user2,show,List.of(seat3)));
        paid(held);assertEquals(2,db.list(Ticket.class,"").size());Ticket first=db.first(Ticket.class,"where e.orderId=?1",alreadyPaid.id).orElseThrow();checkin.checkIn(staff,first.id,show);assertEquals("USED",db.get(Ticket.class,first.id).status);
        assertEquals(302,post(c,"/admin/manage/movies/"+movie+"/restore","_csrf="+URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8)).statusCode());assertTrue(db.get(Movie.class,movie).active);assertEquals(200,get(c,"/movies/phim-kiem-thu").statusCode());assertThrows(IllegalArgumentException.class,()->admin.movieAction(5L,movie,"delete"));
        tx.executeWithoutResult(status->{Movie m=db.get(Movie.class,movie);m.releaseDate=LocalDate.of(2026,11,1);m.endDate=LocalDate.of(2026,11,20);});assertTrue(catalog.movies(null,null,null,null,null).isEmpty());assertEquals(200,get(c,"/movies/phim-kiem-thu").statusCode(),"Ticket holders can still open an ended movie");assertThrows(IllegalArgumentException.class,()->booking.hold(user2,show,List.of(seat3)),"Shows after the end date take no new bookings");
    }
    @Test void unusedMovieDeleteIsAtomicAndNeverDeletesCustomerRelations(){
        Long id=tx.execute(status->{Movie m=dated(new Movie());m.title="Chưa dùng";m.slug="chua-dung";db.save(m);Genre g=new Genre();g.name="Thể loại";db.save(g);MovieGenre link=new MovieGenre();link.movieId=m.id;link.genreId=g.id;db.save(link);Person p=new Person();p.name="Người tham gia";db.save(p);Credit credit=new Credit();credit.movieId=m.id;credit.personId=p.id;db.save(credit);return m.id;});
        admin.movieAction(5L,id,"delete");assertTrue(db.first(Movie.class,"where e.id=?1",id).isEmpty());assertEquals(0L,db.count(MovieGenre.class,"where e.movieId=?1",id));assertEquals(2L,db.count(Genre.class,""));assertEquals(1L,db.count(Person.class,""));
        Long linked=tx.execute(status->{Movie m=dated(new Movie());m.title="Đã yêu thích";m.slug="da-yeu-thich";db.save(m);Favorite f=new Favorite();f.userId=user1;f.movieId=m.id;db.save(f);return m.id;});assertThrows(IllegalArgumentException.class,()->admin.movieAction(5L,linked,"delete"));assertNotNull(db.get(Movie.class,linked));assertEquals(1L,db.count(Favorite.class,"where e.movieId=?1",linked));
    }
    @Test void previewCountsAllConflictsPaginatesAndIncludesPreviousDay()throws Exception{
        Long movie=db.get(Showtime.class,show).movieId;
        tx.executeWithoutResult(status->{for(int n=0;n<7;n++){Showtime s=new Showtime();s.movieId=movie;s.roomId=room;s.startsAt=Instant.parse("2026-12-01T00:00:00Z").plusSeconds(n*7200);s.endsAt=s.startsAt.plusSeconds(3600);s.status="DRAFT";db.save(s);}});
        var preview=showtimeEditor.preview(cinema,room,LocalDate.of(2026,12,1),movie,LocalDateTime.of(2026,12,1,19,45),null,0);assertEquals(2L,preview.get("conflictCount"));var data=(vn.cineprime.admin.AdminPage<?>)preview.get("data");assertEquals(5,data.rows().size());assertEquals(8L,data.total());assertTrue(data.rows().getFirst().toString().contains("conflict=true"));
        assertEquals(3,((vn.cineprime.admin.AdminPage<?>)showtimeEditor.preview(cinema,room,LocalDate.of(2026,12,1),null,null,null,1).get("data")).rows().size());
        assertThrows(IllegalArgumentException.class,()->showtimeEditor.preview(db.get(User.class,otherStaff).cinemaId,room,LocalDate.of(2026,12,1),movie,null,null,0));
        Long otherRoom=tx.execute(status->{Room r=new Room();r.cinemaId=cinema;r.name="Phòng đêm";db.save(r);Showtime s=new Showtime();s.movieId=movie;s.roomId=r.id;s.startsAt=Instant.parse("2026-12-01T16:30:00Z");s.endsAt=Instant.parse("2026-12-01T18:15:00Z");s.status="OPEN";db.save(s);return r.id;});
        assertEquals(1L,showtimeEditor.preview(cinema,otherRoom,LocalDate.of(2026,12,2),movie,LocalDateTime.of(2026,12,2,1,29),null,0).get("conflictCount"));assertEquals(0L,showtimeEditor.preview(cinema,otherRoom,LocalDate.of(2026,12,2),movie,LocalDateTime.of(2026,12,2,1,30),null,0).get("conflictCount"));
        HttpClient c=client();login(c,"admin@example.test");assertEquals(200,get(c,"/api/admin/showtime-preview?cinemaId="+cinema+"&roomId="+room+"&date=2026-12-01&movieId="+movie+"&startsAt=2026-12-01T19:45&page=0").statusCode());HttpClient st=client();login(st,"staff@example.test");assertEquals(403,get(st,"/api/admin/showtime-preview?cinemaId="+cinema+"&roomId="+room+"&date=2026-12-01").statusCode());
    }
    Map<String,String> showFields(Showtime s){return Map.of("movieId",s.movieId.toString(),"roomId",s.roomId.toString(),"startsAt",s.startsAt.atZone(vn.cineprime.admin.ReportService.ZONE).toLocalDateTime().toString(),"language","Phụ đề","format","2D","status",s.status,"surcharge",Long.toString(s.surcharge));}
    @Test void scheduleRaceAndProtectedEndRemainCorrect()throws Exception{
        Showtime original=db.get(Showtime.class,show);Map<String,String> fields=new HashMap<>(showFields(original));fields.put("startsAt","2026-12-01T22:00");fields.put("cinemaId",cinema.toString());var result=race(()->createShow(fields),()->createShow(fields));assertEquals(1,result.stream().filter(Boolean::booleanValue).count());
        HttpClient c=client();login(c,"admin@example.test");String token=csrf(get(c,"/admin/showtimes/new?cinemaId="+cinema).body());
        String body="movieId="+original.movieId+"&roomId="+room+"&cinemaId="+cinema+"&startsAt=2026-12-02T03%3A00&language=Test&format=2D&status=DRAFT&surcharge=0&_csrf="+URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8);
        var saved=get(c,"/admin/manage/showtimes/edit");assertEquals(302,saved.statusCode());var response=post(c,"/admin/manage/showtimes/save",body);assertEquals(302,response.statusCode());String destination=response.headers().firstValue("location").orElseThrow();assertTrue(destination.contains("cinemaId="+cinema));assertTrue(destination.contains("date=2026-12-02"));URI location=URI.create(destination);assertEquals(200,getFollowing(c,location.getRawPath()+"?"+location.getRawQuery()).statusCode());
        Map<String,String> wrong=new HashMap<>(fields);wrong.put("cinemaId",db.get(User.class,otherStaff).cinemaId.toString());wrong.put("startsAt","2026-12-02T03:00");assertThrows(IllegalArgumentException.class,()->createShow(wrong));
        Order o=booking.hold(user1,show,List.of(seat1));paid(o);Instant end=original.endsAt;tx.executeWithoutResult(status->db.get(Movie.class,original.movieId).duration=180);Map<String,String> edit=new HashMap<>(showFields(original));edit.put("language","Khác");admin.save(5L,"showtimes",show,edit);assertEquals(end,db.get(Showtime.class,show).endsAt);assertEquals(0L,showtimeEditor.preview(cinema,room,LocalDate.of(2026,12,1),original.movieId,original.startsAt.atZone(vn.cineprime.admin.ReportService.ZONE).toLocalDateTime(),show,0).get("conflictCount"));
    }
    @Test void hideAndHoldRaceKeepsOnlyEarlierHoldsAndBlocksLaterOrders()throws Exception{
        Long movie=db.get(Showtime.class,show).movieId;var result=race(()->{admin.remove(5L,"movies",movie);return null;},()->booking.hold(user1,show,List.of(seat1)));assertTrue(result.getFirst());assertFalse(db.get(Movie.class,movie).active);assertThrows(IllegalArgumentException.class,()->booking.hold(user2,show,List.of(seat2)));if(result.get(1)){Order old=db.first(Order.class,"where e.userId=?1",user1).orElseThrow();paid(old);assertEquals("PAID",db.get(Order.class,old.id).status);}else assertEquals(0L,db.count(Order.class,""));
    }
    Movie dated(Movie m){m.releaseDate=LocalDate.of(2026,11,1);m.endDate=LocalDate.of(2027,1,31);return m;}
    Map<String,String> movieFields(Long id){Movie m=id==null?dated(new Movie()):db.get(Movie.class,id);Map<String,String> v=new HashMap<>();for(String name:AdminService.RESOURCES.get("movies").fields().split(","))v.put(name,Objects.toString(db.view(m).get(name),""));if(id==null){v.put("title","Phim có thể loại");v.put("slug","phim-co-the-loai");v.put("genresPresent","true");v.put("genreIds",genre.toString());}return v;}
    String formBody(Map<String,String> fields){return fields.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),java.nio.charset.StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),java.nio.charset.StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));}
    void selectedOption(String html,String selectId,String value){Matcher select=Pattern.compile("(?s)<select[^>]*id=\""+Pattern.quote(selectId)+"\"[^>]*>.*?</select>").matcher(html);assertTrue(select.find(),html);Matcher option=Pattern.compile("<option\\b[^>]*>").matcher(select.group());boolean found=false;while(option.find()){String tag=option.group();if(tag.contains("value=\""+value+"\"")&&Pattern.compile("\\sselected(?:[=>\\s])").matcher(tag).find())found=true;}assertTrue(found,select.group());}
    vn.cineprime.admin.RoomSetup.SeatInput input(Long id,String label,String row,int position,String type,String pair,boolean active){return new vn.cineprime.admin.RoomSetup.SeatInput(id,label,row,position,type,pair,active);}
    Long emptyRoom(){return tx.execute(status->{Room r=new Room();r.cinemaId=cinema;r.name="Phòng thiết lập";db.save(r);return r.id;});}
    @Test void movieGenresRoundTripAndInactiveGuardAreAtomic()throws Exception{
        Long second=tx.execute(status->{Genre g=new Genre();g.name="Khoa học";db.save(g);return g.id;});HttpClient c=client();login(c,"admin@example.test");String html=get(c,"/admin/manage/movies/edit").body();assertTrue(html.contains("movie-genre-chips"));String token=csrf(html);Map<String,String> fields=movieFields(null);fields.put("genreIds",genre.toString());fields.put("_csrf",token);
        var response=post(c,"/admin/manage/movies/save",formBody(fields)+"&genreIds="+second);assertEquals(302,response.statusCode(),response.body());Movie m=db.first(Movie.class,"where e.slug='phim-co-the-loai'").orElseThrow();assertEquals(Set.of(genre,second),movieGenres.ids(m.id));assertEquals(1,catalog.movies("Phim có thể loại","",second,"",null).size());assertTrue(org.springframework.web.util.HtmlUtils.htmlUnescape(get(c,"/movies?genre="+second).body()).contains("Phim có thể loại"));
        Map<String,String> edit=movieFields(m.id);edit.put("title","Đã sửa");admin.save(5L,"movies",m.id,edit);assertEquals(Set.of(genre,second),movieGenres.ids(m.id),"Legacy payload keeps genre relations");
        edit.put("genresPresent","true");edit.put("genreIds",genre.toString());admin.save(5L,"movies",m.id,edit);assertEquals(Set.of(genre),movieGenres.ids(m.id));assertThrows(IllegalArgumentException.class,()->admin.remove(5L,"genres",genre));assertTrue(db.get(Genre.class,genre).active);
        edit.put("genreIds","");assertThrows(IllegalArgumentException.class,()->admin.save(5L,"movies",m.id,edit));assertEquals(Set.of(genre),movieGenres.ids(m.id));edit.put("genreIds","999999");assertThrows(IllegalArgumentException.class,()->admin.save(5L,"movies",m.id,edit));assertEquals("Đã sửa",db.get(Movie.class,m.id).title);
        Long relation=db.first(MovieGenre.class,"where e.movieId=?1",m.id).orElseThrow().id;assertThrows(IllegalArgumentException.class,()->admin.remove(5L,"movie-genres",relation));assertNotNull(db.get(MovieGenre.class,relation));
        edit.put("genreIds",second.toString());admin.save(5L,"movies",m.id,edit);assertThrows(IllegalArgumentException.class,()->admin.remove(5L,"genres",second));assertTrue(db.get(Genre.class,second).active);
    }
    @Test void inactiveGenresRequireReplacementAndHiddenMoviesCanRemainUnclassified(){
        Long movie=db.get(Showtime.class,show).movieId;assertThrows(IllegalArgumentException.class,()->admin.save(5L,"genres",genre,Map.of("name","Phiêu lưu","active","false")));assertTrue(db.get(Genre.class,genre).active);
        admin.remove(5L,"movies",movie);admin.remove(5L,"genres",genre);assertFalse(db.get(Genre.class,genre).active);assertEquals(Set.of(genre),movieGenres.ids(movie));assertThrows(IllegalArgumentException.class,()->admin.movieAction(5L,movie,"restore"));Map<String,String> fields=movieFields(null);assertThrows(IllegalArgumentException.class,()->admin.save(5L,"movies",null,fields));fields.put("active","false");fields.put("genreIds","");assertDoesNotThrow(()->admin.save(5L,"movies",null,fields));
    }
    @Test void genreEditsAndDeactivationCannotLeaveVisibleMovieUnclassified()throws Exception{
        Long movie=db.get(Showtime.class,show).movieId;Long other=tx.execute(status->{Genre g=new Genre();g.name="Khác";db.save(g);return g.id;});Map<String,String> fields=movieFields(movie);fields.put("genresPresent","true");fields.put("genreIds",other.toString());var result=race(()->admin.save(5L,"movies",movie,fields),()->{admin.remove(5L,"genres",other);return null;});assertTrue(result.get(0)||result.get(1));assertTrue(db.query("select count(g) from "+db.entityName(Genre.class)+" g, "+db.entityName(MovieGenre.class)+" x where x.genreId=g.id and x.movieId=?1 and g.active=true",Long.class,movie).getFirst()>0);
    }
    @Test void roomSetupCreatesExplicitPairsAndRejectsPartialOrReferencedChanges()throws Exception{
        Long newRoom=emptyRoom();var first=input(null,"A1","A",1,"COUPLE","A-pair",true);var second=input(null,"A2","A",2,"COUPLE","A-pair",true);assertThrows(IllegalArgumentException.class,()->setup.create(5L,newRoom,List.of(first)));assertEquals(0,db.list(Seat.class,"where e.roomId=?1",newRoom).size());
        var raceResult=race(()->{setup.create(5L,newRoom,List.of(first,second));return null;},()->{setup.create(5L,newRoom,List.of(first,second));return null;});assertEquals(1,raceResult.stream().filter(Boolean::booleanValue).count());var pair=db.list(Seat.class,"where e.roomId=?1 order by e.position",newRoom);assertEquals(2,pair.size());
        assertThrows(IllegalArgumentException.class,()->setup.change(5L,newRoom,List.of(input(pair.getFirst().id,"A1","A",1,"COUPLE","A-pair",false)),List.of()));assertTrue(db.get(Seat.class,pair.getFirst().id).active);
        setup.disable(5L,pair.getFirst().id);assertEquals(0,db.list(Seat.class,"where e.roomId=?1 and e.active=true",newRoom).size());
        setup.change(5L,newRoom,List.of(input(pair.get(0).id,"A1","A",1,"COUPLE","A-pair",true),input(pair.get(1).id,"A2","A",2,"COUPLE","A-pair",true)),List.of());assertThrows(IllegalArgumentException.class,()->setup.change(5L,newRoom,List.of(),List.of(pair.getFirst().id)));assertEquals(2,db.list(Seat.class,"where e.roomId=?1",newRoom).size());
        setup.change(5L,newRoom,List.of(),pair.stream().map(s->s.id).toList());assertEquals(0,db.list(Seat.class,"where e.roomId=?1",newRoom).size());assertThrows(IllegalArgumentException.class,()->setup.change(5L,room,List.of(),List.of(seat1)));
        admin.generateSeats(5L,newRoom,2,3);assertEquals(6,db.list(Seat.class,"where e.roomId=?1 and e.type='STANDARD'",newRoom).size(),"Legacy generator defaults to Standard, no hidden VIP/Couple split");
        String oldVersion=setup.version(newRoom);var s=db.first(Seat.class,"where e.roomId=?1",newRoom).orElseThrow();setup.change(5L,newRoom,List.of(input(s.id,s.label,s.rowLabel,s.position,"VIP","",true)),List.of(),oldVersion);assertThrows(IllegalArgumentException.class,()->setup.change(5L,newRoom,List.of(input(s.id,s.label,s.rowLabel,s.position,"STANDARD","",true)),List.of(),oldVersion));assertEquals("VIP",db.get(Seat.class,s.id).type);
        tx.executeWithoutResult(status->{Showtime past=new Showtime();past.roomId=newRoom;past.movieId=db.get(Showtime.class,show).movieId;past.startsAt=clock.instant().minusSeconds(7200);past.endsAt=clock.instant().minusSeconds(3600);past.status="CANCELLED";db.save(past);Inventory inv=new Inventory();inv.showtimeId=past.id;inv.seatId=s.id;db.save(inv);});assertFalse(setup.busy(newRoom));assertThrows(IllegalArgumentException.class,()->setup.change(5L,newRoom,List.of(),List.of(s.id)));assertNotNull(db.get(Seat.class,s.id));
    }
    @Test void dailyScheduleAndShowtimeListKeepRoomMovieAndDateContext()throws Exception{
        LocalDate day=LocalDate.of(2026,12,1);Long movie=db.get(Showtime.class,show).movieId;Long empty=emptyRoom();
        assertEquals(1L,adminReads.schedule(day,cinema,room,movie,"","OPEN",0).get("total"));
        assertEquals(0L,adminReads.schedule(day,cinema,empty,movie,"","OPEN",0).get("total"));
        HttpClient c=client();login(c,"admin@example.test");String params="cinemaId="+cinema+"&roomId="+room+"&movieId="+movie+"&date="+day+"&q=&state=OPEN";
        String daily=get(c,"/admin/schedule?"+params).body();assertTrue(daily.contains("Quản lý suất chiếu"));assertTrue(daily.contains(("/admin/manage/showtimes?"+params).replace("&","&amp;")));selectedOption(daily,"schedule-room",room.toString());
        String shows=get(c,"/admin/manage/showtimes?"+params).body();assertTrue(shows.contains(("/admin/schedule?"+params).replace("&","&amp;")));
    }
    @Test void setupContextReadinessAndFormContextRespectCinemaAndActualTime()throws Exception{
        Long newRoom=emptyRoom();setup.create(5L,newRoom,List.of(input(null,"A1","A",1,"VIP","",true)));Long movie=db.get(Showtime.class,show).movieId;Instant start=clock.instant().plusSeconds(3600);
        assertEquals(false,setup.readiness(cinema,newRoom,movie,start,null).get("canOpen"));Map<String,String> price=Map.of("cinemaId",cinema.toString(),"seatType","VIP","dayType","WEEKDAY","fromMinute","1020","toMinute","1440","amount","123457");admin.save(5L,"prices",null,price);assertEquals(true,setup.readiness(cinema,newRoom,movie,start,null).get("canOpen"));assertEquals(false,setup.readiness(cinema,newRoom,movie,start.minusSeconds(7200),null).get("canOpen"));
        assertThrows(IllegalArgumentException.class,()->setup.context(db.get(User.class,otherStaff).cinemaId,newRoom));HttpClient c=client();login(c,"admin@example.test");for(String path:List.of("/admin/setup/rooms/"+newRoom,"/admin/setup/rooms/"+newRoom+"?step=prices","/admin/setup/rooms/"+room+"?step=inventory","/api/admin/setup-context?roomId="+newRoom)){var response=get(c,path);assertEquals(200,response.statusCode(),path+response.body().substring(0,Math.min(400,response.body().length())));}
        String form=get(c,"/admin/manage/prices/edit?cinemaId="+cinema+"&setupRoomId="+newRoom+"&seatType=VIP&dayType=WEEKEND").body();selectedOption(form,"field-cinemaId",cinema.toString());selectedOption(form,"field-dayType","WEEKEND");assertTrue(form.contains("name=\"amount\" value=\"\""));assertTrue(form.contains("name=\"setupRoomId\" value=\""+newRoom+"\""));
        HttpClient st=client();login(st,"staff@example.test");assertEquals(403,get(st,"/api/admin/setup-context?roomId="+newRoom).statusCode());assertEquals(403,get(st,"/api/admin/rooms/"+room+"/inventory?showtimeId="+show).statusCode());
    }
    @Test void heldSeatInventoryCancelsWholeOrderAndLatePaymentStaysInReconciliation()throws Exception{
        Order order=booking.hold(user1,show,List.of(seat1,seat2));booking.updateCart(user1,order.id,Map.of(),"LAST");Payment payment=payment(order);assertTrue(setup.inventory(room,show).toString().contains("canCancel=true"));HttpClient c=client();login(c,"admin@example.test");String token=csrf(get(c,"/admin/setup/rooms/"+room+"?step=inventory").body());String data="viewRoomId="+room+"&viewShowtimeId="+show+"&_csrf="+URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(403,post(c,"/admin/orders/"+order.id+"/cancel","viewRoomId="+room).statusCode());assertEquals(302,post(c,"/admin/orders/"+order.id+"/cancel",data).statusCode());assertEquals("CANCELLED",db.get(Order.class,order.id).status);assertEquals(0L,db.count(Inventory.class,"where e.status='HELD'"));assertEquals("RELEASED",db.first(VoucherUse.class,"where e.orderId=?1",order.id).orElseThrow().status);booking.paymentResult(payment.externalOrderId,payment.amount,"after-admin-cancel",0);assertTrue(db.get(Payment.class,payment.id).reconciliation);assertEquals(0L,db.count(Ticket.class,""));assertFalse(setup.inventory(room,show).toString().contains("canCancel=true"));
        Order paidOrder=booking.hold(user2,show,List.of(seat3));paid(paidOrder);assertEquals(302,post(c,"/admin/orders/"+paidOrder.id+"/cancel","_csrf="+URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8)).statusCode());assertEquals("PAID",db.get(Order.class,paidOrder.id).status);assertEquals("SOLD",db.first(Inventory.class,"where e.seatId=?1",seat3).orElseThrow().status);Order expired=booking.hold(user1,show,List.of(seat1));clock.advance(300);assertEquals(302,post(c,"/admin/orders/"+expired.id+"/cancel","_csrf="+URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8)).statusCode());assertEquals("EXPIRED",db.get(Order.class,expired.id).status);assertThrows(IllegalArgumentException.class,()->setup.inventory(room,999999L));
    }
    @Test void setupHttpFlowKeepsCinemaAndRejectsCsrfAndStaleLayout()throws Exception{
        Long other=db.get(User.class,otherStaff).cinemaId;HttpClient c=client();login(c,"admin@example.test");String token=csrf(get(c,"/admin/manage/rooms/edit?cinemaId="+other).body());String body="cinemaId="+other+"&name=Ph%C3%B2ng+1&format=2D&active=true&next=continue&_csrf="+URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8);var created=post(c,"/admin/manage/rooms/save",body);assertEquals(302,created.statusCode());Room r=db.first(Room.class,"where e.cinemaId=?1",other).orElseThrow();assertTrue(created.headers().firstValue("location").orElseThrow().endsWith("/admin/setup/rooms/"+r.id));
        String version=setup.version(r.id);String json="{\"version\":\""+version+"\",\"seats\":[{\"label\":\"A1\",\"rowLabel\":\"A\",\"position\":1,\"type\":\"STANDARD\",\"active\":true}],\"deleteIds\":[]}";assertEquals(403,staffJson(c,"/admin/layout?roomId="+r.id,json,"invalid").statusCode());var saved=staffJson(c,"/admin/layout?roomId="+r.id,json,token);assertEquals(200,saved.statusCode(),saved.body());assertFalse(saved.body().contains("nextUrl"));assertTrue(saved.body().contains("version"));assertEquals(400,staffJson(c,"/admin/layout?roomId="+r.id,json,token).statusCode());assertEquals(1L,db.count(Seat.class,"where e.roomId=?1",r.id));
        String showForm=getFollowing(c,"/admin/manage/showtimes/edit?cinemaId="+other+"&roomId="+r.id).body();selectedOption(showForm,"batch-cinema",other.toString());selectedOption(showForm,"batch-room",r.id.toString());
        String wrong="cinemaId="+cinema+"&setupRoomId="+r.id+"&seatType=VIP&dayType=WEEKDAY&fromMinute=0&toMinute=1440&amount=98765&_csrf="+URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8);assertEquals(400,post(c,"/admin/manage/prices/save",wrong).statusCode());assertEquals(0L,db.count(Price.class,"where e.amount=98765"));
    }
    @Test void loginRateLimitStillRejectsSixteenthAttemptWithinOneWindow()throws Exception{
        HttpClient c=client();String token=csrf(get(c,"/login").body()),body="email=admin%40example.test&password=WrongPassword2026!&_csrf="+URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8);
        for(int n=0;n<15;n++)assertEquals(302,post(c,"/login",body).statusCode());assertEquals(429,post(c,"/login",body).statusCode());
    }
    @Test void roomFinishSavesOnlyTheRoomAndWorkspaceKeepsOptionalSetup()throws Exception{
        HttpClient c=client();login(c,"admin@example.test");String html=get(c,"/admin/manage/rooms/edit?cinemaId="+cinema).body(),token=csrf(html);assertTrue(html.contains("Lưu và kết thúc"));
        var saved=post(c,"/admin/manage/rooms/save","cinemaId="+cinema+"&name=Finish&format=2D&active=true&next=finish&_csrf="+URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8));assertEquals(302,saved.statusCode());assertTrue(saved.headers().firstValue("location").orElseThrow().contains("/admin/manage/rooms?cinemaId="+cinema));Room r=db.first(Room.class,"where e.name='Finish'").orElseThrow();assertEquals(0L,db.count(Seat.class,"where e.roomId=?1",r.id));assertEquals(0L,db.count(Showtime.class,"where e.roomId=?1",r.id));
        String workspace=get(c,"/admin/setup/rooms/"+r.id+"?step=prices").body();assertTrue(workspace.contains("seat-workspace"));assertTrue(workspace.contains("workspace-price-form"));assertTrue(workspace.contains("Thêm suất chiếu (tùy chọn)"));assertTrue(org.springframework.web.util.HtmlUtils.htmlUnescape(workspace).contains("/admin/showtimes/new?cinemaId="+cinema+"&roomId="+r.id));assertFalse(workspace.contains("4. Tạo suất"));
    }
    @Test void inlinePriceHttpRoundTripScopeVersionAndSecurity()throws Exception{
        Long r=emptyRoom();HttpClient c=client();login(c,"admin@example.test");String token=csrf(get(c,"/admin/setup/rooms/"+r).body()),url="/api/admin/rooms/"+r+"/prices";
        String json="{\"cinemaId\":\"999999\",\"seatType\":\"COUPLE\",\"dayType\":\"WEEKEND\",\"fromMinute\":\"00:00\",\"toMinute\":\"24:00\",\"amount\":\"95001\"}";assertEquals(403,staffJson(c,url,json,"invalid").statusCode());assertEquals(200,staffJson(c,url,json,token).statusCode());Price p=db.first(Price.class,"where e.amount=95001").orElseThrow();assertEquals(cinema,p.cinemaId);assertEquals(1440,p.toMinute);var read=get(c,url);assertEquals(200,read.statusCode());assertTrue(read.headers().firstValue("Cache-Control").orElse("").contains("no-store"));assertTrue(read.body().contains("95001"));assertTrue(read.body().contains("24:00"));
        String edit=json.replace("95001","95003").replace("}",",\"priceVersion\":\""+vn.cineprime.admin.RoomPrices.version(p)+"\"}");assertEquals(200,staffJson(c,url+"/"+p.id,edit,token).statusCode());assertEquals(409,staffJson(c,url+"/"+p.id,edit,token).statusCode());assertEquals(95003,db.get(Price.class,p.id).amount);
        for(String amount:List.of("0","-1","95.5"))assertEquals(400,staffJson(c,url,json.replace("95001",amount).replace("COUPLE","VIP"),token).statusCode());assertEquals(400,staffJson(c,url,json.replace("24:00","24:01"),token).statusCode());
        Long otherRoom=tx.execute(status->{Room other=new Room();other.cinemaId=db.get(User.class,otherStaff).cinemaId;other.name="Scope";db.save(other);return other.id;});assertEquals(400,staffJson(c,"/api/admin/rooms/"+otherRoom+"/prices/"+p.id,edit,token).statusCode());HttpClient st=client();login(st,"staff@example.test");assertEquals(403,get(st,url).statusCode());HttpClient customer=client();login(customer,"one@example.test");assertEquals(403,get(customer,url).statusCode());
    }
    @Test void inlinePricesPaginateAndSerializeDuplicateAndStaleWrites()throws Exception{
        Long r=emptyRoom();Map<String,String> fields=Map.of("seatType","VIP","dayType","WEEKEND","fromMinute","00:00","toMinute","24:00","amount","100001");assertEquals(1L,race(()->roomPrices.save(5L,r,null,fields),()->roomPrices.save(5L,r,null,fields)).stream().filter(Boolean::booleanValue).count());Price p=db.first(Price.class,"where e.seatType='VIP'").orElseThrow();String version=vn.cineprime.admin.RoomPrices.version(p);Map<String,String> edit=new HashMap<>(fields);edit.put("priceVersion",version);edit.put("amount","100003");assertEquals(1L,race(()->roomPrices.save(5L,r,p.id,edit),()->roomPrices.save(5L,r,p.id,edit)).stream().filter(Boolean::booleanValue).count());
        for(int n=0;n<11;n++)roomPrices.save(5L,r,null,Map.of("seatType","VIP","dayType","WEEKDAY","fromMinute",Integer.toString(n*120),"toMinute",Integer.toString((n+1)*120),"amount","123457"));var first=roomPrices.page(r,"VIP","WEEKDAY",0);assertEquals(11L,first.get("total"));assertEquals(5,((List<?>)first.get("rows")).size());assertEquals(5,((List<?>)roomPrices.page(r,"VIP","WEEKDAY",1).get("rows")).size());assertEquals(1,((List<?>)roomPrices.page(r,"VIP","WEEKDAY",2).get("rows")).size());
    }
    @Test void inlinePriceChangePreservesOrderSnapshotsAndOpenShowCoverage()throws Exception{
        Order pending=booking.hold(user1,show,List.of(seat1));Order paidOrder=booking.hold(user2,show,List.of(seat3));paid(paidOrder);clock.advance(1);long before=(long)reports.create("day",LocalDate.of(2026,12,1),null).model().get("revenueValue");Price p=db.first(Price.class,"where e.seatType='STANDARD'").orElseThrow();Map<String,String> fields=new HashMap<>(Map.of("seatType","STANDARD","dayType","WEEKDAY","fromMinute","00:00","toMinute","24:00","amount","90001","priceVersion",vn.cineprime.admin.RoomPrices.version(p)));roomPrices.save(5L,room,p.id,fields);assertEquals(85000,db.first(OrderSeat.class,"where e.orderId=?1",pending.id).orElseThrow().price);assertEquals(85000,db.first(OrderSeat.class,"where e.orderId=?1",paidOrder.id).orElseThrow().price);assertEquals(before,reports.create("day",LocalDate.of(2026,12,1),null).model().get("revenueValue"));
        fields.put("priceVersion",vn.cineprime.admin.RoomPrices.version(db.get(Price.class,p.id)));fields.put("toMinute","17:00");assertThrows(IllegalArgumentException.class,()->roomPrices.save(5L,room,p.id,fields));assertEquals(1440,db.get(Price.class,p.id).toMinute);Order next=booking.hold(user2,show,List.of(seat2));assertEquals(90001,db.first(OrderSeat.class,"where e.orderId=?1",next.id).orElseThrow().price);
    }
    @Test void catalogGenreQueriesDoNotGrowPerMovieOrAssociation(){
        tx.executeWithoutResult(status->{for(int n=0;n<12;n++){Movie m=dated(new Movie());m.title="Nhóm liên kết "+n;m.slug="nhom-lien-ket-"+n;db.save(m);MovieGenre link=new MovieGenre();link.movieId=m.id;link.genreId=genre;db.save(link);}});var stats=entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();stats.setStatisticsEnabled(true);stats.clear();try{assertEquals(13,catalog.movies("","",genre,"",null).size());assertTrue(stats.getPrepareStatementCount()<=4,"Movie query plus two grouped metadata queries");}finally{stats.setStatisticsEnabled(false);}
    }
    @Test void unassignedStaffAndNewCatalogCanCompleteFreeBookingAfterSetup()throws Exception{
        HttpClient st=client();login(st,"staff@example.test");tx.executeWithoutResult(status->{User s=db.get(User.class,staff);s.cinemaId=null;s.authVersion++;});assertEquals(401,get(st,"/api/staff/showtimes/"+show+"/tickets").statusCode());login(st,"staff@example.test");String unassigned=org.springframework.web.util.HtmlUtils.htmlUnescape(get(st,"/staff").body());assertTrue(unassigned.contains("Chưa được phân công rạp"));
        Cinema cinema2=(Cinema)admin.save(5L,"cinemas",null,Map.of("name","Rạp mới","city","Hà Nội","active","true"));Room room2=(Room)admin.save(5L,"rooms",null,Map.of("cinemaId",cinema2.id.toString(),"name","Phòng mới","format","2D","active","true"));admin.generateSeats(5L,room2.id,2,4);
        for(String type:List.of("STANDARD","VIP","COUPLE"))admin.save(5L,"prices",null,Map.of("cinemaId",cinema2.id.toString(),"seatType",type,"dayType","WEEKDAY","fromMinute","0","toMinute","1440","amount","85000"));
        Movie movie=(Movie)admin.save(5L,"movies",null,Map.of("genresPresent","true","genreIds",genre.toString(),"title","Phim tự nhập","slug","phim-tu-nhap","duration","100","ageRating","P","releaseDate","2026-11-01","endDate","2027-01-31","active","true"));Showtime pending=new Showtime();pending.movieId=movie.id;pending.roomId=room2.id;pending.startsAt=clock.instant().plusSeconds(3600);pending.status="OPEN";Showtime created=createShow(showFields(pending));
        admin.save(5L,"users",staff,Map.of("name","Staff","email","staff@example.test","role","STAFF","cinemaId",cinema2.id.toString(),"enabled","true"));assertEquals(1,((List<?>)staffReads.page(staff,LocalDate.of(2026,12,1),null).get("shows")).size());
        Long seat=db.first(Seat.class,"where e.roomId=?1 and e.label='A1'",room2.id).orElseThrow().id;Order order=booking.hold(user1,created.id,List.of(seat));assertEquals(0L,reports.create("day",LocalDate.of(2026,12,1),null).model().get("revenueValue"));assertEquals(0L,db.count(Ticket.class,""));booking.updateCart(user1,order.id,Map.of(),"LAST");assertEquals("PENDING_PAYMENT",db.get(Order.class,order.id).status);assertEquals(0L,db.count(Ticket.class,""));
        tx.executeWithoutResult(status->db.get(Voucher.class,voucher).value=100);booking.updateCart(user1,order.id,Map.of(),"LAST");booking.completeFree(user1,order.id);Ticket ticket=db.first(Ticket.class,"where e.orderId=?1",order.id).orElseThrow();checkin.checkIn(staff,ticket.id,created.id);assertEquals("USED",db.get(Ticket.class,ticket.id).status);clock.advance(1);assertEquals(0L,reports.create("day",LocalDate.of(2026,12,1),null).model().get("revenueValue"));assertEquals(1,reports.create("day",LocalDate.of(2026,12,1),null).model().get("orderCount"));
    }
    @Autowired vn.cineprime.admin.MovieCredits movieCredits;
    @Test void movieWizardCreditsSaveAtomicallyAndLegacyPayloadKeepsRelations()throws Exception{
        Long person=tx.execute(status->{Person p=new Person();p.name="Người tham gia tích hợp";db.save(p);return p.id;});
        Map<String,String> fields=movieFields(null);fields.put("creditsPresent","true");fields.put("directorIds",person+","+person);fields.put("actorIds",person.toString());Movie m=(Movie)admin.save(5L,"movies",null,fields);
        assertEquals(Set.of(person),movieCredits.ids(m.id).get("DIRECTOR"));assertEquals(Set.of(person),movieCredits.ids(m.id).get("ACTOR"));assertEquals(2L,db.count(Credit.class,"where e.movieId=?1",m.id));
        Map<String,String> legacy=movieFields(m.id);legacy.put("title","Tên đã sửa");admin.save(5L,"movies",m.id,legacy);assertEquals(2L,db.count(Credit.class,"where e.movieId=?1",m.id));
        Map<String,String> invalid=new HashMap<>(fields);invalid.put("slug","khong-luu-do-dang");invalid.put("actorIds","999999");long before=db.count(Movie.class,"");var problem=assertThrows(vn.cineprime.admin.AdminFormProblem.class,()->admin.save(5L,"movies",null,invalid));assertEquals(List.of("actorIds"),problem.fields());assertEquals(before,db.count(Movie.class,""));assertEquals(0L,db.count(Movie.class,"where e.slug='khong-luu-do-dang'"));
        String detail=org.springframework.web.util.HtmlUtils.htmlUnescape(get(client(),"/movies/"+m.slug).body());assertTrue(detail.contains("Người tham gia tích hợp"));
        Map<String,String> clear=movieFields(m.id);clear.put("creditsPresent","true");admin.save(5L,"movies",m.id,clear);assertEquals(0L,db.count(Credit.class,"where e.movieId=?1",m.id));
    }
    HttpResponse<String> wizardPost(HttpClient c,String path,String body)throws Exception{return c.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/x-www-form-urlencoded").header("X-Admin-Form","steps").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());}
    @Test void wizardJsonErrorsKeepFieldLocationsAndRoomFinishAndCsrf()throws Exception{
        HttpClient c=client();login(c,"admin@example.test");String html=get(c,"/admin/manage/movies/edit").body(),token=csrf(html);assertTrue(html.contains("data-picker-name=\"directorIds\""));assertTrue(html.contains("/js/admin-movie.js"));assertTrue(html.contains("name=\"endDate\""));
        Map<String,String> fields=movieFields(null);fields.put("_csrf",token);fields.put("trailer","https://not-youtube.example/watch?v=123");String body=fields.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),java.nio.charset.StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),java.nio.charset.StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
        var invalid=wizardPost(c,"/admin/manage/movies/save",body);assertEquals(400,invalid.statusCode());assertTrue(invalid.body().contains("\"fields\":[\"trailer\"]"));assertEquals(1L,db.count(Movie.class,""));assertTrue(invalid.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        String roomBody="cinemaId="+cinema+"&name=WizardFinish&format=2D&active=true&next=finish&_csrf="+URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8);var saved=wizardPost(c,"/admin/manage/rooms/save",roomBody);assertEquals(200,saved.statusCode());assertTrue(saved.body().contains("/admin/manage/rooms?cinemaId="+cinema+"&saved"));Room r=db.first(Room.class,"where e.name='WizardFinish'").orElseThrow();assertEquals(0L,db.count(Seat.class,"where e.roomId=?1",r.id));assertEquals(0L,db.count(Showtime.class,"where e.roomId=?1",r.id));assertEquals(403,wizardPost(c,"/admin/manage/rooms/save",roomBody.replace(token,"invalid")).statusCode());
        var passwordError=wizardPost(c,"/admin/manage/users/save","email=bad&name=Test&role=CUSTOMER&enabled=true&initialPassword=SecretTestNeverEcho&_csrf="+URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8));assertEquals(400,passwordError.statusCode());assertTrue(passwordError.body().contains("\"passwordRequired\":true"));assertFalse(passwordError.body().contains("SecretTestNeverEcho"));
    }
    @Test void wizardOptionPagingAndConcurrentCreditsDoNotMultiplyQueriesOrLinks()throws Exception{
        Long movie=db.get(Showtime.class,show).movieId;List<Long> people=tx.execute(status->{List<Long> result=new ArrayList<>();for(int n=0;n<11;n++){Person p=new Person();p.name="Người số "+n;db.save(p);result.add(p.id);}return result;});
        HttpClient c=client();login(c,"admin@example.test");var first=get(c,"/api/admin/form-options?kind=people&page=0&selected="+people.getLast());assertEquals(200,first.statusCode());assertTrue(first.body().contains("\"total\":11"));assertTrue(first.body().contains("Người số 10"));var mapper=new tools.jackson.databind.ObjectMapper();assertEquals(5,mapper.readTree(first.body()).get("rows").size());assertEquals(1,mapper.readTree(get(c,"/api/admin/form-options?kind=people&page=2").body()).get("rows").size());assertTrue(first.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        Map<String,String> fields=movieFields(movie);fields.put("creditsPresent","true");fields.put("directorIds",people.getFirst().toString());fields.put("actorIds",people.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(",")));assertEquals(2L,race(()->admin.save(5L,"movies",movie,fields),()->admin.save(5L,"movies",movie,fields)).stream().filter(Boolean::booleanValue).count());assertEquals(12L,db.count(Credit.class,"where e.movieId=?1",movie));
        var stats=entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();stats.setStatisticsEnabled(true);try{stats.clear();get(client(),"/movies/"+db.get(Movie.class,movie).slug);long count=stats.getPrepareStatementCount();assertTrue(count<23,"Credits are batch-read; queries="+count);}finally{stats.setStatisticsEnabled(false);}
        HttpClient staffClient=client();login(staffClient,"staff@example.test");assertEquals(403,get(staffClient,"/api/admin/form-options?kind=people").statusCode());HttpClient customer=client();login(customer,"one@example.test");assertEquals(403,get(customer,"/api/admin/form-options?kind=genres").statusCode());
    }
}
