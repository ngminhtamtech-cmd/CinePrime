package vn.cineprime.domain;

import jakarta.persistence.*;
import java.lang.reflect.Field;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import static vn.cineprime.domain.Domain.*;

@Repository
@Transactional(readOnly=true)
public class Store {
    @org.springframework.beans.factory.annotation.Autowired private Clock clock=Clock.systemUTC();
    @PersistenceContext public EntityManager em;
    public EntityManager entityManager(){return em;}
    public String entityName(Class<?> type) { return em.getMetamodel().entity(type).getName(); }
    public <T> T get(Class<T> type, Long id) {
        T row = id==null ? null : em.find(type,id);
        if(row==null) throw new IllegalArgumentException("Không tìm thấy dữ liệu.");
        return row;
    }
    public <T> T lock(Class<T> type, Long id) {
        T row=em.find(type,id);
        if(row==null) throw new IllegalArgumentException("Không tìm thấy dữ liệu.");
        // find(..., lockMode) does not refresh an entity already loaded into this persistence context.
        // Refresh once on first lock; preserve pending edits when the same aggregate is re-locked.
        if(em.getLockMode(row)!=LockModeType.PESSIMISTIC_WRITE)em.refresh(row,LockModeType.PESSIMISTIC_WRITE);
        return row;
    }
    public <T> T lockRead(Class<T> type,Long id){T row=get(type,id);LockModeType mode=em.getLockMode(row);if(mode!=LockModeType.PESSIMISTIC_READ&&mode!=LockModeType.PESSIMISTIC_WRITE)em.refresh(row,LockModeType.PESSIMISTIC_READ);return row;}
    public <T> List<T> list(Class<T> type,String where,Object... args) {
        String entity=em.getMetamodel().entity(type).getName();
        TypedQuery<T> q=em.createQuery("select e from "+entity+" e "+where,type);
        for(int i=0;i<args.length;i++) q.setParameter(i+1,args[i]);
        return q.getResultList();
    }
    public <T> List<T> query(String hql,Class<T> resultType,Object... args) {
        TypedQuery<T> q=em.createQuery(hql,resultType);
        for(int i=0;i<args.length;i++)q.setParameter(i+1,args[i]);
        return q.getResultList();
    }
    public <T> long count(Class<T> type,String where,Object... args) {
        return query("select count(e) from "+em.getMetamodel().entity(type).getName()+" e "+where,Long.class,args).getFirst();
    }
    public <T> Optional<T> first(Class<T> type,String where,Object... args) {
        TypedQuery<T> q=em.createQuery("select e from "+em.getMetamodel().entity(type).getName()+" e "+where,type);
        for(int i=0;i<args.length;i++)q.setParameter(i+1,args[i]);
        return q.setMaxResults(1).getResultList().stream().findFirst();
    }
    @Transactional public <T extends Row> T save(T row) { if(row.id==null) {em.persist(row); return row;} return em.merge(row); }
    @Transactional public void delete(Row row) { em.remove(em.contains(row)?row:em.merge(row)); }
    public Map<String,Object> view(Row row) {
        Map<String,Object> result=new LinkedHashMap<>();
        for(Field f:row.getClass().getFields()) {
            if(Set.of("passwordHash","tokenHash","token").contains(f.getName())) continue;
            try {result.put(f.getName(),f.get(row));} catch(IllegalAccessException e) {throw new IllegalStateException(e);}
        }
        if(row instanceof Movie movie)result.put("status",MovieLifecycle.status(movie,MovieLifecycle.today(clock)));
        return result;
    }
    public List<Map<String,Object>> views(Collection<? extends Row> rows) { return rows.stream().map(this::view).toList(); }
    public String money(long amount) { return String.format(Locale.forLanguageTag("vi-VN"),"%,d ₫",amount); }
    public String date(Instant instant) { return instant==null?"":DateTimeFormatter.ofPattern("HH:mm · dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(instant); }
    public String local(Instant instant) { return instant==null?"":DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(instant); }
    @Transactional public void audit(Long actor,String action,String target) { Audit a=new Audit();a.actorId=actor;a.action=action;a.target=target;a.createdAt=Instant.now();save(a); }
}
