package vn.cineprime.admin;

import jakarta.servlet.http.HttpSession;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.cineprime.domain.Store;
import vn.cineprime.domain.MovieLifecycle;
import static vn.cineprime.domain.Domain.*;

/** Financial reads never invoke booking/payment mutations. Every download uses its rendered snapshot. */
@Service
public class ReportService {
    public static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String CACHE=ReportService.class.getName()+".snapshots";
    private final Store db; private final Clock clock;
    public ReportService(Store db,Clock clock){this.db=db;this.clock=clock;}
    public record Range(String period,LocalDate from,LocalDate until,Instant end,LocalDate previousFrom,Instant previousEnd,boolean future){}
    public record Snapshot(String id,Instant createdAt,Map<String,Object> model,byte[] csv){
        public Snapshot {model=Collections.unmodifiableMap(new LinkedHashMap<>(model));csv=csv.clone();}
        @Override public byte[] csv(){return csv.clone();}
    }
    public static Range range(String period,LocalDate date,Instant now){
        if(!Set.of("day","week","month","year").contains(period))throw new IllegalArgumentException("Kỳ báo cáo không hợp lệ.");
        LocalDate from=switch(period){case "day"->date;case "week"->date.minusDays(date.getDayOfWeek().getValue()-1);case "year"->date.withDayOfYear(1);default->date.withDayOfMonth(1);};
        LocalDate until=shift(from,period,1),previous=shift(from,period,-1);
        Instant start=from.atStartOfDay(ZONE).toInstant(),finish=until.atStartOfDay(ZONE).toInstant();
        boolean future=!now.isAfter(start);Instant end=now.isBefore(finish)?now:finish;
        LocalDateTime progress=now.atZone(ZONE).toLocalDateTime();
        LocalDateTime previousProgress=switch(period){case "day"->progress.minusDays(1);case "week"->progress.minusWeeks(1);case "year"->progress.minusYears(1);default->progress.minusMonths(1);};
        Instant previousEnd=now.isBefore(finish)?previousProgress.atZone(ZONE).toInstant():start;
        if(previousEnd.isBefore(previous.atStartOfDay(ZONE).toInstant()))previousEnd=previous.atStartOfDay(ZONE).toInstant();
        return new Range(period,from,until,end,previous,previousEnd,future);
    }
    private static LocalDate shift(LocalDate d,String period,int n){return switch(period){case "day"->d.plusDays(n);case "week"->d.plusWeeks(n);case "year"->d.plusYears(n);default->d.plusMonths(n);};}
    private List<Object[]> paid(Instant from,Instant end,Long cinemaId){
        return db.query("select o,s.movieId,r.cinemaId,u.email from %s o, %s s, %s r, %s u where o.showtimeId=s.id and s.roomId=r.id and o.userId=u.id and o.status='PAID' and o.paidAt>=?1 and o.paidAt<?2".formatted(db.entityName(Order.class),db.entityName(Showtime.class),db.entityName(Room.class),db.entityName(User.class))+(cinemaId==null?"":" and r.cinemaId=?3")+" order by o.paidAt,o.id",Object[].class,cinemaId==null?new Object[]{from,end}:new Object[]{from,end,cinemaId});
    }
    private long tickets(Instant from,Instant end,Long cinemaId){
        return db.query("select count(l) from %s l, %s o, %s s, %s r where l.orderId=o.id and o.showtimeId=s.id and s.roomId=r.id and o.status='PAID' and o.paidAt>=?1 and o.paidAt<?2".formatted(db.entityName(OrderSeat.class),db.entityName(Order.class),db.entityName(Showtime.class),db.entityName(Room.class))+(cinemaId==null?"":" and r.cinemaId=?3"),Long.class,cinemaId==null?new Object[]{from,end}:new Object[]{from,end,cinemaId}).getFirst();
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Snapshot create(String period,LocalDate date,Long cinemaId){
        Instant now=clock.instant();if(date==null)date=now.atZone(ZONE).toLocalDate();Range range=range(period,date,now);
        String cinema=cinemaId==null?"Tất cả rạp":db.get(Cinema.class,cinemaId).name;
        List<Object[]> rows=range.future()?List.of():paid(range.from().atStartOfDay(ZONE).toInstant(),range.end(),cinemaId);
        List<Object[]> previous=range.future()?List.of():paid(range.previousFrom().atStartOfDay(ZONE).toInstant(),range.previousEnd(),cinemaId);
        long revenue=total(rows),oldRevenue=total(previous),ticketCount=range.future()?0:tickets(range.from().atStartOfDay(ZONE).toInstant(),range.end(),cinemaId),oldTickets=range.future()?0:tickets(range.previousFrom().atStartOfDay(ZONE).toInstant(),range.previousEnd(),cinemaId);
        String id=UUID.randomUUID().toString();Map<String,Object> model=new LinkedHashMap<>();
        model.put("reportId",id);model.put("period",period);model.put("reportDate",date.toString());model.put("cinemaId",cinemaId==null?"":cinemaId.toString());model.put("cinemaLabel",cinema);model.put("generatedAt",stamp(now));model.put("range",range.from()+" → "+range.until().minusDays(1));
        model.put("cutoff",range.future()?"Chưa bắt đầu":stamp(range.end()));model.put("comparisonRange",range.future()?"Không so sánh kỳ chưa bắt đầu":"từ 00:00 · "+range.previousFrom().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))+" đến trước "+stamp(range.previousEnd()));
        model.put("revenue",db.money(revenue));model.put("revenueValue",revenue);model.put("ticketCount",ticketCount);model.put("orderCount",rows.size());
        model.put("revenueChange",change(revenue,oldRevenue,range.future()));model.put("ticketChange",change(ticketCount,oldTickets,range.future()));model.put("orderChange",change(rows.size(),previous.size(),range.future()));
        model.put("chart",chart(range,now,rows));model.put("topMovies",top(rows));model.put("occupancy",occupancy(range,cinemaId));
        model.put("customerCount",db.count(User.class,"where e.role='CUSTOMER'"));model.put("nowCount",db.count(Movie.class,"where e.active=true and "+MovieLifecycle.expression("e","?1")+"='NOW_SHOWING'",MovieLifecycle.today(clock)));model.put("comingCount",db.count(Movie.class,"where e.active=true and "+MovieLifecycle.expression("e","?1")+"='COMING_SOON'",MovieLifecycle.today(clock)));
        StringBuilder csv=new StringBuilder("\uFEFFMã báo cáo,Kỳ,Rạp,Mốc chốt,Mã đơn,Thanh toán lúc,Phim,Rạp trên đơn,Phòng,Email,Tạm tính VND,Giảm VND,Doanh thu VND\r\n");
        for(Object[] row:rows){Order o=(Order)row[0];csv.append(csvRow(id,range.from()+" → "+range.until().minusDays(1),cinema,range.end(),o.code,stamp(o.paidAt),o.movieTitle,o.cinemaName,o.roomName,row[3],o.subtotal,o.discount,o.total));}
        return new Snapshot(id,now,model,csv.toString().getBytes(StandardCharsets.UTF_8));
    }
    private static String stamp(Instant time){return DateTimeFormatter.ofPattern("HH:mm:ss · dd/MM/yyyy").withZone(ZONE).format(time);}
    private static long total(List<Object[]> rows){long total=0;for(Object[] row:rows)total=Math.addExact(total,((Order)row[0]).total);return total;}
    private static String change(long current,long previous,boolean future){if(future)return "Chưa bắt đầu";if(previous==0)return "— · Kỳ trước bằng 0";BigDecimal percent=BigDecimal.valueOf(current).subtract(BigDecimal.valueOf(previous)).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(previous),1,RoundingMode.HALF_UP);return (percent.signum()>0?"↑ +":percent.signum()<0?"↓ ":"↔ ")+percent.toPlainString()+"% so cùng kỳ";}
    private List<Map<String,Object>> chart(Range range,Instant now,List<Object[]> rows){
        int count=range.period().equals("day")?24:range.period().equals("year")?12:(int)java.time.temporal.ChronoUnit.DAYS.between(range.from(),range.until());long[] sums=new long[count];
        for(Object[] row:rows){Order o=(Order)row[0];ZonedDateTime t=o.paidAt.atZone(ZONE);int bucket=range.period().equals("day")?t.getHour():range.period().equals("year")?t.getMonthValue()-1:(int)java.time.temporal.ChronoUnit.DAYS.between(range.from(),t.toLocalDate());sums[bucket]=Math.addExact(sums[bucket],o.total);}
        long max=Arrays.stream(sums).max().orElse(0);List<Map<String,Object>> points=new ArrayList<>();
        for(int i=0;i<count;i++){Instant start=range.period().equals("day")?range.from().atStartOfDay(ZONE).plusHours(i).toInstant():range.period().equals("year")?range.from().plusMonths(i).atStartOfDay(ZONE).toInstant():range.from().plusDays(i).atStartOfDay(ZONE).toInstant();boolean future=!start.isBefore(now);int height=max==0?0:BigDecimal.valueOf(sums[i]).multiply(BigDecimal.valueOf(150)).divide(BigDecimal.valueOf(max),0,RoundingMode.HALF_UP).intValue();String label=range.period().equals("day")?String.format("%02dh",i):range.period().equals("year")?"T"+(i+1):range.from().plusDays(i).format(DateTimeFormatter.ofPattern("dd/MM"));points.add(Map.of("label",label,"value",sums[i],"amount",future?"Chưa tới":db.money(sums[i]),"future",future,"x",i*32+12,"y",170-height,"height",height,"labelX",i*32+21,"labelY",i%2==0?192:207));}
        return List.copyOf(points);
    }
    private List<Map<String,Object>> top(List<Object[]> rows){
        Map<Long,Long> amounts=new HashMap<>();Map<Long,String> names=new HashMap<>();for(Object[] row:rows){Order o=(Order)row[0];Long movieId=(Long)row[1];amounts.merge(movieId,o.total,Math::addExact);names.put(movieId,o.movieTitle);}
        return amounts.entrySet().stream().sorted(Map.Entry.<Long,Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).map(e->Map.<String,Object>of("title",names.get(e.getKey())+" · #"+e.getKey(),"amount",db.money(e.getValue()),"value",e.getValue())).toList();
    }
    private List<Map<String,Object>> occupancy(Range range,Long cinemaId){
        Object[] args=cinemaId==null?new Object[]{range.from().atStartOfDay(ZONE).toInstant(),range.until().atStartOfDay(ZONE).toInstant()}:new Object[]{range.from().atStartOfDay(ZONE).toInstant(),range.until().atStartOfDay(ZONE).toInstant(),cinemaId};
        String scope="s.startsAt>=?1 and s.startsAt<?2 and s.status<>'CANCELLED'"+(cinemaId==null?"":" and r.cinemaId=?3");
        Map<Long,Long> capacities=new HashMap<>(),sold=new HashMap<>();
        db.query("select s.id,count(i) from %s s, %s r, %s i, %s seat where s.roomId=r.id and i.showtimeId=s.id and i.seatId=seat.id and seat.active=true and ".formatted(db.entityName(Showtime.class),db.entityName(Room.class),db.entityName(Inventory.class),db.entityName(Seat.class))+scope+" group by s.id",Object[].class,args).forEach(row->capacities.put((Long)row[0],(Long)row[1]));
        db.query("select s.id,count(t) from %s s, %s r, %s t where s.roomId=r.id and t.showtimeId=s.id and t.status in ('VALID','USED') and ".formatted(db.entityName(Showtime.class),db.entityName(Room.class),db.entityName(Ticket.class))+scope+" group by s.id",Object[].class,args).forEach(row->sold.put((Long)row[0],(Long)row[1]));
        return db.query("select s,m.title,r.name,c.name from %s s, %s r, %s c, %s m where s.roomId=r.id and r.cinemaId=c.id and s.movieId=m.id and ".formatted(db.entityName(Showtime.class),db.entityName(Room.class),db.entityName(Cinema.class),db.entityName(Movie.class))+scope+" order by s.startsAt,s.id",Object[].class,args).stream().map(row->{Showtime s=(Showtime)row[0];long n=sold.getOrDefault(s.id,0L),capacity=capacities.getOrDefault(s.id,0L);return Map.<String,Object>of("id",s.id,"movieTitle",row[1],"roomName",row[2],"cinemaName",row[3],"when",db.date(s.startsAt),"occupancy",n+" / "+capacity+" ("+(capacity==0?"—":n*100/capacity+"%")+")");}).toList();
    }
    public void remember(HttpSession session,Snapshot snapshot){synchronized(session){var cache=cache(session);cache.entrySet().removeIf(e->expired(e.getValue()));cache.put(snapshot.id(),snapshot);while(cache.size()>2)cache.remove(cache.keySet().iterator().next());}}
    public Snapshot recall(HttpSession session,String id){synchronized(session){var cache=cache(session);Snapshot snapshot=cache.get(id);if(snapshot==null||expired(snapshot)){cache.remove(id);throw new IllegalArgumentException("Báo cáo đã hết hạn, vui lòng làm mới báo cáo.");}return snapshot;}}
    @SuppressWarnings("unchecked") public AdminPage<Map<String,Object>> section(Snapshot snapshot,String section,int page){
        if(!Set.of("chart","topMovies","occupancy").contains(section))throw new IllegalArgumentException("Phần báo cáo không hợp lệ.");
        return AdminPage.slice((List<Map<String,Object>>)snapshot.model().get(section),page);
    }
    private boolean expired(Snapshot snapshot){return !clock.instant().isBefore(snapshot.createdAt().plusSeconds(900));}
    @SuppressWarnings("unchecked") private LinkedHashMap<String,Snapshot> cache(HttpSession session){Object value=session.getAttribute(CACHE);if(value instanceof LinkedHashMap<?,?>)return (LinkedHashMap<String,Snapshot>)value;var cache=new LinkedHashMap<String,Snapshot>();session.setAttribute(CACHE,cache);return cache;}
    public static String csvCell(Object value){String text=Objects.toString(value,"");if(text.matches("^[=+@\\-\\t\\r].*"))text="'"+text;return "\""+text.replace("\"","\"\"")+"\"";}
    public static String csvRow(Object... cells){return String.join(",",Arrays.stream(cells).map(ReportService::csvCell).toList())+"\r\n";}
}
