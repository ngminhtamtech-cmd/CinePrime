package vn.cineprime.notification;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import vn.cineprime.booking.BookingService;
import vn.cineprime.domain.Store;
import static vn.cineprime.domain.Domain.*;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="cineprime.jobs-enabled",havingValue="true",matchIfMissing=true)
public class Workers {
    private final Store db;private final BookingService bookings;private final Notifications notices;private final JavaMailSender sender;private final Clock clock;private final TransactionTemplate tx;
    @Value("${cineprime.mail-enabled}") boolean mailEnabled;
    @Value("${cineprime.mail-from}") String from;
    public Workers(Store db,BookingService bookings,Notifications notices,JavaMailSender sender,Clock clock,PlatformTransactionManager tm){this.db=db;this.bookings=bookings;this.notices=notices;this.sender=sender;this.clock=clock;this.tx=new TransactionTemplate(tm);}
    @Scheduled(fixedDelay=15000,initialDelay=15000) public void expiry(){
        for(Order o:db.list(Order.class,"where e.status='PENDING_PAYMENT' and e.expiresAt<=?1",clock.instant()))try{bookings.expire(o.id);}catch(RuntimeException ignored){/* Next pass retries; transaction rolls back on races. */}
    }
    @Scheduled(fixedDelay=60000,initialDelay=30000) public void reminders(){
        for(Order snapshot:db.list(Order.class,"where e.status='PAID' and e.showStartsAt>?1 and e.showStartsAt<=?2",clock.instant(),clock.instant().plusSeconds(7200)))tx.executeWithoutResult(status->{
            User u=db.lock(User.class,snapshot.userId);Order o=db.get(Order.class,snapshot.id);
            if(o.createdAt.isBefore(o.showStartsAt.minusSeconds(7200)))notices.notify(u,"remind:"+o.id,"Sắp đến giờ xem phim",o.movieTitle+" · "+o.cinemaName+" · "+db.date(o.showStartsAt),true);
        });
        for(Subscription snapshot:db.list(Subscription.class,"where e.sent=false"))tx.executeWithoutResult(status->{
            User u=db.lock(User.class,snapshot.userId);Subscription s=db.lock(Subscription.class,snapshot.id);if(s.sent)return;
            if(!db.list(Showtime.class,"where e.movieId=?1 and e.status='OPEN' and e.startsAt>?2",s.movieId,clock.instant()).isEmpty()){
                Movie m=db.get(Movie.class,s.movieId);notices.notify(u,"movie:"+s.id,"Phim bạn chờ đã mở bán",m.title+" đã có lịch chiếu. Khám phá và đặt vé tại CinePrime.",s.email);s.sent=true;
            }
        });
    }
    @Scheduled(fixedDelay=15000,initialDelay=20000) public void sendMail(){
        if(!mailEnabled)return;
        for(MailJob snapshot:db.list(MailJob.class,"where e.sentAt is null and e.nextAttemptAt<=?1 order by e.id",clock.instant()).stream().limit(20).toList())tx.executeWithoutResult(status->{
            MailJob j=db.lock(MailJob.class,snapshot.id);if(j.sentAt!=null||j.nextAttemptAt.isAfter(clock.instant()))return;
            try{SimpleMailMessage message=new SimpleMailMessage();message.setFrom(from);message.setTo(j.destination);message.setSubject(j.subject);message.setText(j.body);sender.send(message);j.sentAt=clock.instant();}
            catch(RuntimeException ex){j.attempts++;j.nextAttemptAt=clock.instant().plusSeconds(Math.min(3600,30L*(1L<<Math.min(j.attempts,6))));}
        });
    }
}
