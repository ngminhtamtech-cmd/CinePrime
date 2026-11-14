package vn.cineprime.admin;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cineprime.domain.Store;
import vn.cineprime.web.Catalog;
import static vn.cineprime.domain.Domain.*;

/** Database-filtered management reads. Never changes booking, payment or pricing state. */
@Service @Transactional(readOnly=true)
public class AdminReads {
    private final Store db; private final Catalog catalog; private final Clock clock;
    public AdminReads(Store db,Catalog catalog,Clock clock){this.db=db;this.catalog=catalog;this.clock=clock;}
    private String entity(Class<? extends Row> type){return db.entityName(type);}
    private <T> TypedQuery<T> query(String hql,Class<T> type,Map<String,Object> args){var q=db.entityManager().createQuery(hql,type);args.forEach(q::setParameter);return q;}
    private String search(String q){return "%"+q.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";}
    private AdminPage<Object[]> page(String select,String from,String order,Map<String,Object> args,int requested){
        long total=query("select count(*)"+from,Long.class,args).getSingleResult();int page=AdminPage.number(requested,total);
        var rows=query(select+from+order,Object[].class,args).setFirstResult(page*AdminPage.SIZE).setMaxResults(AdminPage.SIZE).getResultList();
        return new AdminPage<>(rows,total,page,page>0,(page+1L)*AdminPage.SIZE<total);
    }
    private Map<String,Object> mapped(AdminPage<Object[]> p,List<Map<String,Object>> rows){return new AdminPage<>(rows,p.total(),p.page(),p.hasPrevious(),p.hasMore()).model();}
    public Map<String,Object> orderSection(Long id,String section,int requested){
        Class<? extends Row> type=switch(section){case "lines"->OrderSeat.class;case "combos"->OrderProduct.class;case "payments"->Payment.class;case "tickets"->Ticket.class;default->throw new IllegalArgumentException("Phần đơn không hợp lệ.");};
        long total=db.count(type,"where e.orderId=?1",id);int page=AdminPage.number(requested,total);
        var records=query("select e from "+entity(type)+" e where e.orderId=:id order by e.id desc",type,Map.of("id",id)).setFirstResult(page*AdminPage.SIZE).setMaxResults(AdminPage.SIZE).getResultList();
        Map<Long,String> seats=new HashMap<>();if(section.equals("tickets")&&!records.isEmpty())db.query("select s.id,s.label from "+entity(Seat.class)+" s where s.id in ?1",Object[].class,records.stream().map(r->((Ticket)r).seatId).toList()).forEach(r->seats.put((Long)r[0],(String)r[1]));
        List<Map<String,Object>> rows=records.stream().map(row->{
            if(row instanceof OrderSeat l)return Map.<String,Object>of("seat",l.label,"type",AdminPresentation.text(l.seatType),"price",db.cash(l.price));
            if(row instanceof OrderProduct l)return Map.<String,Object>of("name",l.name,"quantity",l.quantity,"price",db.cash(Math.multiplyExact(l.price,l.quantity)));
            var v=db.view(row);if(row instanceof Payment p){v.put("statusLabel",AdminPresentation.text(p.status));v.put("amountText",db.cash(p.amount));}if(row instanceof Ticket t){v.put("seat",seats.getOrDefault(t.seatId,"—"));v.put("statusLabel",AdminPresentation.text(t.status));v.put("when",db.date(t.checkedInAt));}return v;
        }).toList();return new AdminPage<>(rows,total,page,page>0,(page+1L)*AdminPage.SIZE<total).model();
    }
    public static String interval(Instant start,Instant end){
        var a=start.atZone(ReportService.ZONE);var b=end.atZone(ReportService.ZONE);var f=DateTimeFormatter.ofPattern("HH:mm");
        long days=java.time.temporal.ChronoUnit.DAYS.between(a.toLocalDate(),b.toLocalDate());
        return f.format(a)+"–"+f.format(b)+(days>0?" (+"+days+" ngày)":"");
    }
    public List<Map<String,Object>> movies(Long cinema,LocalDate day){
        if(cinema==null)return List.of();
        return query("select distinct m.id,m.title from "+entity(Showtime.class)+" s, "+entity(Movie.class)+" m, "+entity(Room.class)+" r where s.movieId=m.id and s.roomId=r.id and r.cinemaId=:cinema and s.startsAt>=:from and s.startsAt<:until order by m.title,m.id",Object[].class,Map.of("cinema",cinema,"from",day.atStartOfDay(ReportService.ZONE).toInstant(),"until",day.plusDays(1).atStartOfDay(ReportService.ZONE).toInstant())).getResultList().stream().map(r->Map.<String,Object>of("id",r[0],"name",r[1])).toList();
    }
    public Map<String,Object> schedule(LocalDate day,Long cinema,Long movie,String q,String state,int requested){
        return schedule(day,cinema,null,movie,q,state,requested);
    }
    public Map<String,Object> schedule(LocalDate day,Long cinema,Long room,Long movie,String q,String state,int requested){
        String from=" from "+entity(Showtime.class)+" s, "+entity(Movie.class)+" m, "+entity(Room.class)+" r, "+entity(Cinema.class)+" c where s.movieId=m.id and s.roomId=r.id and r.cinemaId=c.id and s.startsAt>=:from and s.startsAt<:until";
        Map<String,Object> args=new HashMap<>(Map.of("from",day.atStartOfDay(ReportService.ZONE).toInstant(),"until",day.plusDays(1).atStartOfDay(ReportService.ZONE).toInstant()));
        if(cinema==null)from+=" and 1=0";else{from+=" and c.id=:cinema";args.put("cinema",cinema);}
        if(room!=null){from+=" and r.id=:room";args.put("room",room);}
        if(movie!=null){from+=" and m.id=:movie";args.put("movie",movie);}if(!state.isBlank()){from+=" and s.status=:state";args.put("state",state);}
        if(!q.isBlank()){from+=" and (lower(m.title) like :q escape '!' or lower(r.name) like :q escape '!')";args.put("q",search(q));}
        var p=page("select s,m.title,r.name,c.name",from," order by m.title,s.startsAt,s.id",args,requested);
        return mapped(p,p.rows().stream().map(row->{var s=(Showtime)row[0];return Map.<String,Object>of("id",s.id,"movieTitle",row[1],"roomName",row[2],"cinemaName",row[3],"when",interval(s.startsAt,s.endsAt),"status",AdminPresentation.text(s.status),"date",s.startsAt.atZone(ReportService.ZONE).toLocalDate().toString());}).toList());
    }
    private static final String[] WEEKDAYS={"Thứ 2","Thứ 3","Thứ 4","Thứ 5","Thứ 6","Thứ 7","Chủ nhật"};
    public static String dayLabel(LocalDate day){return WEEKDAYS[day.getDayOfWeek().getValue()-1]+" · "+DateTimeFormatter.ofPattern("dd/MM/yyyy").format(day);}
    private static String pct(double v){return String.format(Locale.ROOT,"%.3f%%",v);}
    private static String hm(int minute){return String.format("%02d:%02d",Math.floorMod(minute/60,24),minute%60);}
    /** Sold seats per show (paid tickets hold SOLD inventory). */
    private Map<Long,Long> sold(Collection<Long> shows){Map<Long,Long> out=new HashMap<>();if(shows.isEmpty())return out;query("select i.showtimeId,count(i) from "+entity(Inventory.class)+" i where i.showtimeId in :ids and i.status='SOLD' group by i.showtimeId",Object[].class,Map.of("ids",shows)).getResultList().forEach(r->out.put((Long)r[0],((Number)r[1]).longValue()));return out;}
    /** Active seats per room. */
    private Map<Long,Long> capacity(Collection<Long> rooms){Map<Long,Long> out=new HashMap<>();if(rooms.isEmpty())return out;query("select s.roomId,count(s) from "+entity(Seat.class)+" s where s.roomId in :ids and s.active=true group by s.roomId",Object[].class,Map.of("ids",rooms)).getResultList().forEach(r->out.put((Long)r[0],((Number)r[1]).longValue()));return out;}
    /** Shows with a paid order or a live hold (same rule as AdminService.busyShow), read in one query. */
    private Set<Long> busy(Collection<Long> shows){if(shows.isEmpty())return Set.of();return new HashSet<>(query("select distinct o.showtimeId from "+entity(Order.class)+" o where o.showtimeId in :ids and (o.status='PAID' or (o.status='PENDING_PAYMENT' and o.expiresAt>:now))",Long.class,Map.of("ids",shows,"now",clock.instant())).getResultList());}
    private static String statusTone(String status){return switch(status){case "OPEN"->"info";case "CANCELLED"->"bad";default->"off";};}
    /** "Theo ngày": every show of one day on a time axis, one row per room, with fill rate, the now line and what needs attention. */
    public Map<String,Object> board(LocalDate day,Long cinema,Long room,Long movie,String state){
        Map<String,Object> out=new HashMap<>();Instant now=clock.instant(),dayStart=day.atStartOfDay(ReportService.ZONE).toInstant();
        if(cinema==null){out.put("boardRooms",List.of());out.put("boardHours",List.of());out.put("stats",Map.of("total",0,"open",0,"draft",0,"live",0,"emptyRooms",0,"roomsWithShows",0,"emptyNames",""));out.put("attention",List.of());out.put("showNow",false);return out;}
        String hql="select s,m.title from "+entity(Showtime.class)+" s, "+entity(Movie.class)+" m, "+entity(Room.class)+" r where s.movieId=m.id and s.roomId=r.id and r.cinemaId=:cinema and s.startsAt>=:from and s.startsAt<:until";
        Map<String,Object> args=new HashMap<>(Map.of("cinema",cinema,"from",dayStart,"until",day.plusDays(1).atStartOfDay(ReportService.ZONE).toInstant()));
        if(movie!=null){hql+=" and m.id=:movie";args.put("movie",movie);}
        if(state.isBlank())hql+=" and s.status<>'CANCELLED'";else{hql+=" and s.status=:state";args.put("state",state);}
        List<Object[]> shows=query(hql+" order by s.startsAt,s.id",Object[].class,args).getResultList();
        List<Room> rooms=db.list(Room.class,"where e.cinemaId=?1 order by e.name,e.id",cinema);
        Map<Long,Long> sold=sold(shows.stream().map(r->((Showtime)r[0]).id).toList()),seats=capacity(rooms.stream().map(r->r.id).toList());
        Map<Long,List<Object[]>> byRoom=new HashMap<>();for(Object[] row:shows)byRoom.computeIfAbsent(((Showtime)row[0]).roomId,k->new ArrayList<>()).add(row);
        int from=480,to=1440;
        for(Object[] row:shows){Showtime s=(Showtime)row[0];int st=(int)Duration.between(dayStart,s.startsAt).toMinutes(),cl=(int)Duration.between(dayStart,s.endsAt).toMinutes()+15;from=Math.min(from,st/60*60);to=Math.max(to,Math.min(1800,(cl+59)/60*60));}
        double span=to-from;List<Map<String,Object>> hours=new ArrayList<>();for(int m=from;m<to;m+=60)hours.add(Map.of("left",pct((m-from)/span*100),"label",hm(m)));
        long open=0,draft=0,live=0;int withShows=0;List<String> emptyNames=new ArrayList<>();List<Map<String,Object>> attention=new ArrayList<>(),rows=new ArrayList<>();
        int nowMinute=(int)Duration.between(dayStart,now).toMinutes();
        for(Room r:rooms){
            if(room!=null&&!r.id.equals(room))continue;List<Object[]> list=byRoom.getOrDefault(r.id,List.of());if(!r.active&&list.isEmpty())continue;
            long cap=seats.getOrDefault(r.id,0L);List<Map<String,Object>> bars=new ArrayList<>();List<int[]> busySpans=new ArrayList<>();
            for(Object[] row:list){
                Showtime s=(Showtime)row[0];int st=(int)Duration.between(dayStart,s.startsAt).toMinutes(),en=(int)Duration.between(dayStart,s.endsAt).toMinutes();long n=sold.getOrDefault(s.id,0L);
                boolean isLive=!now.isBefore(s.startsAt)&&now.isBefore(s.endsAt)&&!"CANCELLED".equals(s.status),isPast=!now.isBefore(s.endsAt);
                if("OPEN".equals(s.status))open++;else if("DRAFT".equals(s.status))draft++;if(isLive)live++;if(!"CANCELLED".equals(s.status))busySpans.add(new int[]{st,en+15});
                Map<String,Object> b=new HashMap<>();b.put("id",s.id);b.put("date",day.toString());b.put("title",row[1]);b.put("time",hm(st));b.put("end",hm(en));
                b.put("left",pct((st-from)/span*100));b.put("width",pct((en-st)/span*100));b.put("tailLeft",pct((en-from)/span*100));b.put("tailWidth",pct(15/span*100));
                b.put("fill",pct(cap==0?0:Math.min(100,n*100.0/cap)));b.put("sold",cap==0?n+" vé":n+" / "+cap+" ghế");
                b.put("cls","tl-bar "+s.status.toLowerCase(Locale.ROOT)+(isLive?" live":"")+(isPast?" past":""));
                b.put("label",row[1]+" · "+hm(st)+"–"+hm(en)+" · "+AdminPresentation.text(s.status)+(cap==0?"":" · đã bán "+n+"/"+cap)+(isLive?" · đang chiếu":""));bars.add(b);
            }
            if(list.isEmpty()){if(r.active)emptyNames.add(r.name);}else withShows++;
            // Gaps of 3 hours or more between 09:00 and 23:00 that are still ahead.
            List<Map<String,Object>> gaps=new ArrayList<>();
            if(r.active&&!list.isEmpty()){busySpans.sort(Comparator.comparingInt(a->a[0]));int cursor=Math.max(540,day.equals(now.atZone(ReportService.ZONE).toLocalDate())?(nowMinute+4)/5*5:0);if(day.isBefore(now.atZone(ReportService.ZONE).toLocalDate()))cursor=1440;
                for(int[] b:busySpans){if(b[0]-cursor>=180&&cursor<1380)gaps.add(gap(r,day,cursor,Math.min(b[0],1380),from,span));cursor=Math.max(cursor,(b[1]+4)/5*5);}
                if(1380-cursor>=180)gaps.add(gap(r,day,cursor,1380,from,span));}
            for(var g:gaps)if(attention.size()<4)attention.add(Map.of("tone","warn","chip","Khoảng trống","text",r.name+" trống "+g.get("from")+"–"+g.get("to")+" ("+g.get("length")+")","link",g.get("link"),"linkText","Thêm suất →"));
            Map<String,Object> v=new HashMap<>();v.put("id",r.id);v.put("name",r.name);v.put("meta",r.format+" · "+cap+" ghế · "+list.size()+" suất"+(r.active?"":" · ngừng hoạt động"));v.put("bars",bars);v.put("gaps",gaps);v.put("empty",list.isEmpty());v.put("active",r.active);v.put("addLink","/admin/showtimes/new?cinemaId="+cinema+"&roomId="+r.id+"&date="+day+"&add=1");rows.add(v);
        }
        if(draft>0)attention.add(Map.of("tone","off","chip","Nháp","text",draft+" suất nháp chưa mở bán, khách chưa đặt được","link","/admin/manage/showtimes?cinemaId="+cinema+"&date="+day+"&state=DRAFT","linkText","Mở bán →"));
        if(!emptyNames.isEmpty())attention.add(Map.of("tone","warn","chip","Chưa có suất","text",String.join(", ",emptyNames)+" chưa có suất nào trong ngày","link","/admin/showtimes/new?cinemaId="+cinema+"&date="+day+"&add=1","linkText","Thêm suất →"));
        out.put("boardRooms",rows);out.put("boardHours",hours);out.put("boardTotal",shows.size());
        out.put("stats",Map.of("total",shows.size(),"open",open,"draft",draft,"live",live,"emptyRooms",emptyNames.size(),"roomsWithShows",withShows,"emptyNames",String.join(", ",emptyNames)));out.put("attention",attention);
        boolean showNow=nowMinute>=from&&nowMinute<to;out.put("showNow",showNow);out.put("nowLeft",pct((nowMinute-from)/span*100));out.put("nowLabel",hm(Math.max(0,nowMinute)));
        out.put("boardFrom",from);out.put("boardTo",to);return out;
    }
    private static Map<String,Object> gap(Room r,LocalDate day,int a,int b,int from,double span){int len=b-a;return Map.of("from",hm(a),"to",hm(b),"length",len/60+" giờ"+(len%60==0?"":" "+len%60+" phút"),"left",pct((a-from)/span*100),"width",pct((b-a)/span*100),"link","/admin/showtimes/new?cinemaId="+r.cinemaId+"&roomId="+r.id+"&date="+day+"&time="+hm(a)+"&add=1");}
    public static final int LIST_SIZE=20;
    /** "Danh sách": shows of one day, 20 per page, grouped by room then start time, with sales and what can be done in bulk. */
    public Map<String,Object> showtimeList(LocalDate day,Long cinema,Long room,Long movie,String q,String state,int requested){
        Map<String,Object> out=new HashMap<>();Instant now=clock.instant();
        String from=" from "+entity(Showtime.class)+" s, "+entity(Movie.class)+" m, "+entity(Room.class)+" r where s.movieId=m.id and s.roomId=r.id and s.startsAt>=:from and s.startsAt<:until";
        Map<String,Object> args=new HashMap<>(Map.of("from",day.atStartOfDay(ReportService.ZONE).toInstant(),"until",day.plusDays(1).atStartOfDay(ReportService.ZONE).toInstant()));
        if(cinema==null)from+=" and 1=0";else{from+=" and r.cinemaId=:cinema";args.put("cinema",cinema);}
        if(room!=null){from+=" and r.id=:room";args.put("room",room);}if(movie!=null){from+=" and m.id=:movie";args.put("movie",movie);}
        if(state.isBlank())from+=" and s.status<>'CANCELLED'";else{from+=" and s.status=:state";args.put("state",state);}
        if(!q.isBlank()){from+=" and (lower(m.title) like :q escape '!' or lower(r.name) like :q escape '!')";args.put("q",search(q));}
        long total=query("select count(*)"+from,Long.class,args).getSingleResult();int pages=(int)Math.max(1,(total+LIST_SIZE-1)/LIST_SIZE),page=Math.min(Math.max(0,requested),pages-1);
        List<Object[]> rows=query("select s,m.title,r.name,r.format"+from+" order by r.name,r.id,s.startsAt,s.id",Object[].class,args).setFirstResult(page*LIST_SIZE).setMaxResults(LIST_SIZE).getResultList();
        Map<Long,Long> perRoom=new HashMap<>();query("select r.id,count(s)"+from+" group by r.id",Object[].class,args).getResultList().forEach(r->perRoom.put((Long)r[0],((Number)r[1]).longValue()));
        List<Long> ids=rows.stream().map(r->((Showtime)r[0]).id).toList();Map<Long,Long> sold=sold(ids),seats=capacity(rows.stream().map(r->((Showtime)r[0]).roomId).distinct().toList());Set<Long> busy=busy(ids);
        List<Map<String,Object>> items=new ArrayList<>();Long lastRoom=null;long drafts=0;
        for(Object[] row:rows){
            Showtime s=(Showtime)row[0];long cap=seats.getOrDefault(s.roomId,0L),n=sold.getOrDefault(s.id,0L);boolean locked=busy.contains(s.id),future=s.startsAt.isAfter(now);
            if(!s.roomId.equals(lastRoom)){lastRoom=s.roomId;items.add(Map.of("group",true,"room",row[2],"meta",row[3]+" · "+cap+" ghế · "+perRoom.getOrDefault(s.roomId,0L)+" suất trong ngày"));}
            Map<String,Object> v=new HashMap<>();v.put("group",false);v.put("id",s.id);v.put("date",day.toString());v.put("when",interval(s.startsAt,s.endsAt));v.put("title",row[1]);v.put("room",row[2]);
            v.put("kind",s.format+" · "+s.language);v.put("sold",s.status.equals("DRAFT")?"Chưa bán":n+" / "+cap);v.put("fill",pct(cap==0?0:Math.min(100,n*100.0/cap)));v.put("surcharge",db.cash(s.surcharge));
            v.put("status",AdminPresentation.text(s.status));v.put("tone",statusTone(s.status));v.put("token",ShowtimeQuickEdit.token(s));v.put("draft",s.status.equals("DRAFT"));v.put("locked",locked);
            boolean selectable=future&&!locked&&!s.status.equals("CANCELLED");v.put("selectable",selectable);if(selectable&&s.status.equals("DRAFT"))drafts++;
            v.put("why",s.status.equals("CANCELLED")?"Suất đã huỷ":locked?"Có vé hoặc lượt giữ ghế, không thao tác hàng loạt":!future?"Suất đã bắt đầu":"");items.add(v);
        }
        out.put("rows",items);out.put("total",total);out.put("page",page);out.put("pages",pages);out.put("hasPrevious",page>0);out.put("hasMore",page+1<pages);out.put("pageDrafts",drafts);return out;
    }
    public Map<String,Object> orders(String q,String state,String paymentState,Long cinema,LocalDate day,int requested){return orders(q,state,paymentState,"",cinema,day,requested);}
    /** Orders of both channels; the summary covers every order matching the filter (not only the current page). */
    public Map<String,Object> orders(String q,String state,String paymentState,String channel,Long cinema,LocalDate day,int requested){
        String from=" from "+entity(Order.class)+" o left join "+entity(User.class)+" u on u.id=o.userId left join "+entity(User.class)+" seller on seller.id=o.soldBy, "+entity(Showtime.class)+" s, "+entity(Room.class)+" r where o.showtimeId=s.id and s.roomId=r.id";
        Map<String,Object> args=new HashMap<>();
        if(channel.equals("ONLINE")||channel.equals("COUNTER")){from+=" and o.channel=:channel";args.put("channel",channel);}
        if(!q.isBlank()){from+=" and (lower(o.code) like :q escape '!' or lower(o.movieTitle) like :q escape '!' or lower(u.email) like :q escape '!' or lower(u.name) like :q escape '!')";args.put("q",search(q));}
        if(!state.isBlank()){from+=" and o.status=:state";args.put("state",state);}if(cinema!=null){from+=" and r.cinemaId=:cinema";args.put("cinema",cinema);}
        if(day!=null){from+=" and o.createdAt>=:from and o.createdAt<:until";args.put("from",day.atStartOfDay(ReportService.ZONE).toInstant());args.put("until",day.plusDays(1).atStartOfDay(ReportService.ZONE).toInstant());}
        if(!paymentState.isBlank()){from+=" and exists (select p.id from "+entity(Payment.class)+" p where p.orderId=o.id and "+(paymentState.equals("RECONCILIATION")?"p.reconciliation=true)":"p.status=:payment)");if(!paymentState.equals("RECONCILIATION"))args.put("payment",paymentState);}
        var p=page("select o,coalesce(u.email,''),coalesce(u.name,'Khách vãng lai'),coalesce(seller.name,'')",from," order by o.createdAt desc,o.id desc",args,requested);
        long paidCount=0,paidAmount=0,onlineCount=0,counterCount=0,pending=0,cancelled=0;
        for(Object[] g:query("select o.status,o.channel,count(o),coalesce(sum(o.total),0)"+from+" group by o.status,o.channel",Object[].class,args).getResultList()){long n=((Number)g[2]).longValue();if("PAID".equals(g[0])){paidCount+=n;paidAmount=Math.addExact(paidAmount,((Number)g[3]).longValue());}else if("PENDING_PAYMENT".equals(g[0]))pending+=n;else if("CANCELLED".equals(g[0]))cancelled+=n;if("COUNTER".equals(g[1]))counterCount+=n;else onlineCount+=n;}
        Map<String,Object> summary=new LinkedHashMap<>();summary.put("paidCount",paidCount);summary.put("paidAmount",db.cash(paidAmount));summary.put("onlineCount",onlineCount);summary.put("counterCount",counterCount);summary.put("pending",pending);summary.put("cancelled",cancelled);
        Map<Long,List<Payment>> payments=new HashMap<>();if(!p.rows().isEmpty())db.list(Payment.class,"where e.orderId in ?1 order by e.id desc",p.rows().stream().map(r->((Order)r[0]).id).toList()).forEach(a->payments.computeIfAbsent(a.orderId,n->new ArrayList<>()).add(a));
        Map<String,Object> model=new HashMap<>(mapped(p,p.rows().stream().map(row->{Order o=(Order)row[0];var v=catalog.order(o);v.put("email",row[1]);v.put("customerName",row[2]);v.put("counter",o.channel.equals("COUNTER"));v.put("seller",row[3]);v.put("channelLabel",o.channel.equals("COUNTER")?"Quầy":"Online");v.put("created",db.date(o.createdAt));v.put("statusLabel",AdminPresentation.text(o.status));var ps=payments.getOrDefault(o.id,List.of());v.put("paymentLabel",ps.isEmpty()?"Chưa tạo thanh toán":(ps.getFirst().provider.equals("CASH")?"Tiền mặt · ":"MoMo · ")+AdminPresentation.text(ps.getFirst().status)+(ps.stream().anyMatch(a->a.reconciliation)?" · Cần đối soát":""));return v;}).toList()));
        model.put("summary",summary);return model;
    }
    /** Counter cash shifts; expected cash of open shifts is read live, closed shifts use their snapshot. */
    public Map<String,Object> shifts(Long cinema,LocalDate day,String state,int requested){
        String from=" from "+entity(Shift.class)+" s, "+entity(User.class)+" u, "+entity(Cinema.class)+" c where s.staffId=u.id and s.cinemaId=c.id";Map<String,Object> args=new HashMap<>();
        if(cinema!=null){from+=" and s.cinemaId=:cinema";args.put("cinema",cinema);}if(!state.isBlank()){from+=" and s.status=:state";args.put("state",state);}
        if(day!=null){from+=" and s.openedAt>=:from and s.openedAt<:until";args.put("from",day.atStartOfDay(ReportService.ZONE).toInstant());args.put("until",day.plusDays(1).atStartOfDay(ReportService.ZONE).toInstant());}
        var p=page("select s,u.name,c.name",from," order by s.openedAt desc,s.id desc",args,requested);
        return mapped(p,p.rows().stream().map(row->{Shift s=(Shift)row[0];long cashIn=query("select coalesce(sum(p.amount),0) from "+entity(Payment.class)+" p where p.shiftId=:id and p.provider='CASH' and p.status='SUCCEEDED'",Long.class,Map.of("id",s.id)).getSingleResult();long expected=s.expectedCash!=null?s.expectedCash:s.openingCash+cashIn;
            Map<String,Object> v=new LinkedHashMap<>();v.put("id",s.id);v.put("staff",row[1]);v.put("cinema",row[2]);v.put("opened",db.date(s.openedAt));v.put("closed",s.closedAt==null?"Đang mở":db.date(s.closedAt));v.put("status",AdminPresentation.text(s.status));v.put("opening",db.cash(s.openingCash));v.put("cashIn",db.cash(expected-s.openingCash));v.put("expected",db.cash(expected));v.put("counted",s.countedCash==null?"—":db.cash(s.countedCash));
            v.put("difference",s.countedCash==null?"—":(s.countedCash>expected?"+":"")+db.cash(s.countedCash-expected));v.put("mismatch",s.countedCash!=null&&s.countedCash!=expected);v.put("orders",db.count(Order.class,"where e.shiftId=?1 and e.status='PAID'",s.id));v.put("cancelled",db.count(Order.class,"where e.shiftId=?1 and e.cancelledAt is not null",s.id));v.put("note",Objects.toString(s.note,""));return v;}).toList());
    }
    public Map<String,Object> audit(String q,int requested){
        String from=" from "+entity(Audit.class)+" a left join "+entity(User.class)+" u on u.id=a.actorId where 1=1";Map<String,Object> args=new HashMap<>();
        if(!q.isBlank()){from+=" and (lower(coalesce(u.name,'Hệ thống')) like :q escape '!' or lower(a.action) like :q escape '!' or lower(coalesce(a.target,'')) like :q escape '!')";args.put("q",search(q));}
        var p=page("select a,coalesce(u.name,'Hệ thống')",from," order by a.createdAt desc,a.id desc",args,requested);
        return mapped(p,p.rows().stream().map(row->{var a=(Audit)row[0];return Map.<String,Object>of("when",db.date(a.createdAt),"actor",row[1],"action",a.action,"target",Objects.toString(a.target,"—"));}).toList());
    }
    public Map<String,Object> voucherUsage(Long id,String q,String state,int requested){
        String from=" from "+entity(VoucherUse.class)+" v, "+entity(Order.class)+" o, "+entity(User.class)+" u where v.orderId=o.id and v.userId=u.id and v.voucherId=:id";Map<String,Object> args=new HashMap<>(Map.of("id",id));
        if(!q.isBlank()){from+=" and (lower(o.code) like :q escape '!' or lower(u.email) like :q escape '!' or lower(u.name) like :q escape '!')";args.put("q",search(q));}if(!state.isBlank()){from+=" and v.status=:state";args.put("state",state);}
        var p=page("select v,o.code,u.email",from," order by v.id desc",args,requested);
        Map<String,Object> model=new HashMap<>(mapped(p,p.rows().stream().map(row->{var v=(VoucherUse)row[0];return Map.<String,Object>of("orderId",v.orderId,"orderCode",row[1],"email",row[2],"status",AdminPresentation.text(v.status),"when",db.date(v.expiresAt));}).toList()));
        model.put("used",db.count(VoucherUse.class,"where e.voucherId=?1 and e.status='USED'",id));model.put("reserved",db.count(VoucherUse.class,"where e.voucherId=?1 and e.status='RESERVED' and e.expiresAt>?2",id,clock.instant()));return model;
    }
}
