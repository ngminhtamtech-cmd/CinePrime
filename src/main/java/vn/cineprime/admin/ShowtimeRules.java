package vn.cineprime.admin;

import java.time.*;
import java.util.*;
import vn.cineprime.domain.Store;
import static vn.cineprime.domain.Domain.*;

/** Shared scheduling rules; writers must own the room lock before checking overlaps. */
public final class ShowtimeRules {
    private ShowtimeRules() {}
    public static final int CLEANUP_SECONDS=900;
    public static boolean overlaps(Instant start,Instant end,Instant otherStart,Instant otherEnd){
        return start.isBefore(otherEnd.plusSeconds(CLEANUP_SECONDS))&&end.plusSeconds(CLEANUP_SECONDS).isAfter(otherStart);
    }
    public static List<String> openingErrors(Movie movie,Room room,Cinema cinema,List<Seat> seats,List<Price> prices,boolean visible,Instant start,long surcharge){
        List<String> errors=new ArrayList<>();
        if(!movie.active||"STOPPED".equals(movie.status)||!room.active||!cinema.active)errors.add("Phim/rạp/phòng đã ngừng hoạt động.");
        if(!visible)errors.add("Phim chưa có thể loại hoạt động.");
        List<Seat> active=seats.stream().filter(s->s.active).toList();
        if(active.isEmpty())errors.add("Phòng chưa có ghế hoạt động.");
        for(Seat seat:active)if("COUPLE".equals(seat.type)&&(seat.coupleGroup==null||seat.coupleGroup.isBlank()||active.stream().filter(s->Objects.equals(s.coupleGroup,seat.coupleGroup)).count()!=2)){
            errors.add("Mỗi nhóm ghế đôi phải có đúng 2 ghế hoạt động.");break;
        }
        var local=start.atZone(ReportService.ZONE);String day=local.getDayOfWeek().getValue()>=6?"WEEKEND":"WEEKDAY";int minute=local.getHour()*60+local.getMinute();
        for(String type:active.stream().map(s->s.type).distinct().toList()){
            var matching=prices.stream().filter(p->type.equals(p.seatType)&&day.equals(p.dayType)&&p.fromMinute<=minute&&p.toMinute>minute).toList();
            if(matching.size()!=1)errors.add("Thiếu hoặc trùng giá "+type+" tại giờ suất đang chọn.");
            else try{Math.addExact(matching.getFirst().amount,surcharge);}catch(ArithmeticException e){errors.add("Giá vé cộng phụ thu vượt giới hạn số tiền.");}
        }
        return errors;
    }
    public static List<String> openingErrors(Store db,Showtime show,Movie movie){
        Room room=db.get(Room.class,show.roomId);
        boolean visible=db.query("select count(g) from "+db.entityName(Genre.class)+" g, "+db.entityName(MovieGenre.class)+" x where x.genreId=g.id and x.movieId=?1 and g.active=true",Long.class,movie.id).getFirst()>0;
        return openingErrors(movie,room,db.get(Cinema.class,room.cinemaId),db.list(Seat.class,"where e.roomId=?1",room.id),db.list(Price.class,"where e.cinemaId=?1",room.cinemaId),visible,show.startsAt,show.surcharge);
    }
    public static void inventory(Store db,Showtime show){
        db.entityManager().flush();
        db.entityManager().createNativeQuery("INSERT INTO inventory(showtime_id,seat_id,status) SELECT :show,s.id,'AVAILABLE' FROM seats s WHERE s.room_id=:room AND NOT EXISTS (SELECT 1 FROM inventory i WHERE i.showtime_id=:show AND i.seat_id=s.id)")
            .setParameter("show",show.id).setParameter("room",show.roomId).executeUpdate();
    }
}
