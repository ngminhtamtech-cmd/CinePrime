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
    Long user1,user2,staff,otherStaff,show,room,seat1,seat2,seat3,pair1,pair2,voucher,cinema;
    TransactionTemplate tx;
    @BeforeEach void fixture()throws Exception {
        clock.value.set(Instant.parse("2026-12-01T10:00:00Z"));tx=new TransactionTemplate(tm);
        try(var c=source.getConnection();var statement=c.createStatement()){
            assertTrue(c.getCatalog().equals("cineprime_test"),"Refuse to reset a non-test schema");
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            try{for(String table:List.of("mail_jobs","audit_logs","contents","notifications","subscriptions","reviews","favorites","point_ledger","tickets","payments","voucher_uses","order_products","order_seats","inventory","orders","vouchers","products","prices","showtimes","seats","rooms","credits","movie_genres","movies","people","genres","reset_tokens","users","cinemas"))statement.execute("TRUNCATE TABLE "+table);}finally{statement.execute("SET FOREIGN_KEY_CHECKS=1");}
        }
        tx.executeWithoutResult(status->{
            Cinema c=new Cinema();c.name="Rạp kiểm thử";c.city="TP. Hồ Chí Minh";db.save(c);cinema=c.id;
            Cinema other=new Cinema();other.name="Khác rạp";other.city="Hà Nội";db.save(other);
            user1=accounts.register("one@example.test","Khách Một","CinePrimeTest2026!").id;user2=accounts.register("two@example.test","Khách Hai","CinePrimeTest2026!").id;
            User st=accounts.register("staff@example.test","Nhân viên","CinePrimeTest2026!");st.role="STAFF";st.cinemaId=cinema;staff=st.id;
            User ost=accounts.register("other@example.test","Khác rạp","CinePrimeTest2026!");ost.role="STAFF";ost.cinemaId=other.id;otherStaff=ost.id;
            User administrator=accounts.register("admin@example.test","Quản trị","CinePrimeTest2026!");administrator.role="ADMIN";
            Movie m=new Movie();m.title="Phim kiểm thử tiếng Việt";m.slug="phim-kiem-thu";m.duration=100;m.status="NOW_SHOWING";m.poster="/images/poster-1.svg";m.description="Một câu chuyện để kiểm tra hiển thị tiếng Việt.";m.releaseDate=LocalDate.of(2026,11,1);db.save(m);
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
    @Test void scheduleOverlapIncludesCleaningBuffer(){Showtime existing=db.get(Showtime.class,show);Map<String,String> fields=new HashMap<>();fields.put("movieId",existing.movieId.toString());fields.put("roomId",room.toString());fields.put("startsAt",db.local(existing.endsAt.plusSeconds(899)));fields.put("language","Phụ đề");fields.put("format","2D");fields.put("status","DRAFT");fields.put("surcharge","0");assertThrows(IllegalArgumentException.class,()->admin.save(user1,"showtimes",null,fields));fields.put("startsAt",db.local(existing.endsAt.plusSeconds(900)));assertDoesNotThrow(()->admin.save(user1,"showtimes",null,fields));}
    @Test void activeHoldProtectsSchedule(){Order o=booking.hold(user1,show,List.of(seat1));assertThrows(IllegalArgumentException.class,()->admin.remove(user1,"showtimes",show));assertThrows(IllegalArgumentException.class,()->admin.generateSeats(user1,room,2,4));assertEquals("PENDING_PAYMENT",db.get(Order.class,o.id).status);}
    @Test void invalidSignatureAndWrongRequestAreRejected(){Order o=booking.hold(user1,show,List.of(seat1));Payment p=payment(o);Map<String,Object> body=ipn(p);body.put("signature","forged");assertThrows(IllegalArgumentException.class,()->momo.ipn(body));body.put("signature",signature(body));momo.ipn(body);assertEquals("PAID",db.get(Order.class,o.id).status);}
    Map<String,Object> ipn(Payment p){Map<String,Object> b=new LinkedHashMap<>();b.put("partnerCode","TEST_PARTNER");b.put("amount",p.amount);b.put("extraData","");b.put("message","Success");b.put("orderId",p.externalOrderId);b.put("orderInfo","CinePrime");b.put("orderType","momo_wallet");b.put("payType","qr");b.put("requestId",p.requestId);b.put("responseTime",123L);b.put("resultCode",0);b.put("transId",12345L);return b;}
    String signature(Map<String,Object> b){StringJoiner j=new StringJoiner("&");j.add("accessKey=TEST_ACCESS");for(String k:List.of("amount","extraData","message","orderId","orderInfo","orderType","partnerCode","payType","requestId","responseTime","resultCode","transId"))j.add(k+"="+b.get(k));return MomoService.sign("TEST_SECRET",j.toString());}
    @Test void pdfContainsVietnameseAndQrCanDecode()throws Exception{Order o=booking.hold(user1,show,List.of(seat1));paid(o);Ticket t=db.list(Ticket.class,"").getFirst();try(var pdf=Loader.loadPDF(files.pdf(t))){String text=new PDFTextStripper().getText(pdf);assertTrue(text.contains("Phim kiểm thử tiếng Việt"));assertTrue(text.contains("Ghế: A1"));}var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(files.qr(t)));var bitmap=new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(new com.google.zxing.client.j2se.BufferedImageLuminanceSource(image)));assertEquals("CP1:"+t.token,new com.google.zxing.MultiFormatReader().decode(bitmap).getText());}
    HttpClient client(){return HttpClient.newBuilder().cookieHandler(new CookieManager()).followRedirects(HttpClient.Redirect.NEVER).build();}
    HttpResponse<String> get(HttpClient c,String path)throws Exception{return c.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString());}
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
        HttpClient administrator=client();login(administrator,"admin@example.test");for(String path:List.of("/admin","/admin/orders","/admin/orders/"+o.id,"/admin/upload","/admin/audit")){assertEquals(200,get(administrator,path).statusCode(),path);}for(String key:AdminService.RESOURCES.keySet()){assertEquals(200,get(administrator,"/admin/manage/"+key).statusCode(),key);if(!key.equals("reviews"))assertEquals(200,get(administrator,"/admin/manage/"+key+"/edit").statusCode(),key+" edit");}
        HttpClient st=client();login(st,"staff@example.test");assertEquals(200,get(st,"/staff?date=2026-12-01&showtimeId="+show).statusCode());
    }
    @Test void lockedAccountInvalidatesExistingSession()throws Exception{HttpClient c=client();login(c,"one@example.test");assertEquals(200,get(c,"/account").statusCode());tx.executeWithoutResult(status->db.get(User.class,user1).enabled=false);assertEquals(302,get(c,"/account").statusCode());}
    @Test void zeroTotalVoucherIsRealFreeOrderNotGatewaySimulation(){tx.executeWithoutResult(status->{Voucher v=db.get(Voucher.class,voucher);v.value=100;});Order o=booking.hold(user1,show,List.of(seat1));booking.updateCart(user1,o.id,Map.of(),"LAST");assertEquals(0,db.get(Order.class,o.id).total);booking.completeFree(user1,o.id);booking.completeFree(user1,o.id);assertEquals("PAID",db.get(Order.class,o.id).status);assertEquals("VOUCHER",db.list(Payment.class,"").getFirst().provider);assertEquals(1,db.list(Ticket.class,"").size());assertEquals(0,db.list(Point.class,"").getFirst().points);}
    @Test void resetTokenIsSingleUseAndExpires(){accounts.forgot("one@example.test");MailJob mail=db.list(MailJob.class,"").getFirst();String raw=mail.body.substring(mail.body.indexOf("token=")+6);accounts.reset(raw,"NewCinePrime2026!");assertThrows(IllegalArgumentException.class,()->accounts.reset(raw,"AnotherPassword2026!"));accounts.forgot("one@example.test");MailJob next=db.list(MailJob.class,"order by e.id desc").getFirst();String expired=next.body.substring(next.body.indexOf("token=")+6);clock.advance(1800);assertThrows(IllegalArgumentException.class,()->accounts.reset(expired,"AnotherPassword2026!"));}
    @Test void checkoutChangesAndReadsNeverExtendHold(){Order o=booking.hold(user1,show,List.of(seat1));Instant expiry=o.expiresAt;clock.advance(60);booking.updateCart(user1,o.id,Map.of(),"LAST");assertEquals(expiry,booking.owned(user1,o.id).expiresAt);payment(o);assertThrows(IllegalArgumentException.class,()->booking.updateCart(user1,o.id,Map.of(),""));assertEquals(expiry,db.get(Order.class,o.id).expiresAt);}
    @Test void cancelledOrderWithLateSuccessNeedsReconciliation(){Order o=booking.hold(user1,show,List.of(seat1));Payment p=payment(o);booking.cancel(user1,o.id);booking.paymentResult(p.externalOrderId,p.amount,"cancel-tx",0);assertEquals("CANCELLED",db.get(Order.class,o.id).status);assertTrue(db.get(Payment.class,p.id).reconciliation);assertTrue(db.list(Ticket.class,"").isEmpty());}
    @Test void newAdminViewsRender()throws Exception{HttpClient c=client();login(c,"admin@example.test");for(String path:List.of("/admin/schedule?date=2026-12-01","/admin/layout/"+room,"/admin/voucher-usage/"+voucher)){assertEquals(200,get(c,path).statusCode(),path);}}
    @Test void loginResumesSelectedBooking()throws Exception{HttpClient c=client();assertEquals(302,get(c,"/booking/"+show).statusCode());String token=csrf(get(c,"/login").body());var result=post(c,"/login","email=one%40example.test&password=CinePrimeTest2026!&_csrf="+URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8));assertEquals(302,result.statusCode());assertTrue(result.headers().firstValue("location").orElseThrow().contains("/booking/"+show));assertEquals(200,get(c,"/booking/"+show).statusCode());}
}
