package vn.cineprime.notification;

import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cineprime.domain.Store;
import static vn.cineprime.domain.Domain.*;

@Service @Transactional
public class Notifications {
    private final Store db;private final Clock clock;
    public Notifications(Store db,Clock clock){this.db=db;this.clock=clock;}
    public void mail(User u,String key,String subject,String body){
        if(db.first(MailJob.class,"where e.dedupKey=?1",key).isPresent())return;
        MailJob j=new MailJob();j.userId=u.id;j.dedupKey=key;j.destination=u.email;j.subject=subject;j.body=body;j.nextAttemptAt=clock.instant();db.save(j);
    }
    public void notify(User u,String key,String subject,String body,boolean email){
        // A dedup job is also created for in-app-only notifications, already marked sent.
        if(db.first(MailJob.class,"where e.dedupKey=?1",key).isPresent())return;
        Notification n=new Notification();n.userId=u.id;n.subject=subject;n.body=body;n.createdAt=clock.instant();db.save(n);
        mail(u,key,subject,body);
        if(!email)db.first(MailJob.class,"where e.dedupKey=?1",key).orElseThrow().sentAt=clock.instant();
    }
}
