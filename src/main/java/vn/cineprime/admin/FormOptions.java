package vn.cineprime.admin;

import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.cineprime.domain.Store;
import static vn.cineprime.domain.Domain.*;

@RestController @RequestMapping("/api/admin/form-options") @Transactional(readOnly=true)
public class FormOptions {
    private final Store db;private final AdminService admin;
    public FormOptions(Store db,AdminService admin){this.db=db;this.admin=admin;}
    /** Quick-add from the movie form: reuse an active person with the same name, otherwise create one through the normal admin save path. */
    @PostMapping("/people") @Transactional public ResponseEntity<Map<String,Object>> addPerson(@org.springframework.security.core.annotation.AuthenticationPrincipal vn.cineprime.auth.SecurityConfig.SessionUser user,@RequestBody Map<String,String> body){
        String name=Objects.toString(body.get("name"),"").trim().replaceAll("\\s+"," ");
        if(name.isEmpty())return ResponseEntity.badRequest().body(Map.of("message","Nhập họ tên."));
        if(name.length()>255)return ResponseEntity.badRequest().body(Map.of("message","Họ tên tối đa 255 ký tự."));
        var existing=db.entityManager().createQuery("from "+db.entityName(Person.class)+" p where lower(p.name)=:name and p.active=true order by p.id",Person.class).setParameter("name",name.toLowerCase(Locale.ROOT)).setMaxResults(1).getResultList();
        if(!existing.isEmpty())return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("person",view(existing.getFirst()),"created",false));
        Person person=(Person)admin.save(user.id(),"people",null,Map.of("name",name,"biography","","active","true"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("person",view(person),"created",true));
    }
    /** Quick-add a genre from the movie form ("+ Khác"): reuse the same name (re-activating a stopped one), otherwise create it through the normal admin save path. */
    @PostMapping("/genres") @Transactional public ResponseEntity<Map<String,Object>> addGenre(@org.springframework.security.core.annotation.AuthenticationPrincipal vn.cineprime.auth.SecurityConfig.SessionUser user,@RequestBody Map<String,String> body){
        String name=Objects.toString(body.get("name"),"").trim().replaceAll("\\s+"," ");
        if(name.isEmpty())return ResponseEntity.badRequest().body(Map.of("message","Nhập tên thể loại."));
        if(name.length()>100)return ResponseEntity.badRequest().body(Map.of("message","Tên thể loại tối đa 100 ký tự."));
        var existing=db.entityManager().createQuery("from "+db.entityName(Genre.class)+" g where lower(g.name)=:name order by g.id",Genre.class).setParameter("name",name.toLowerCase(Locale.ROOT)).setMaxResults(1).getResultList();
        if(!existing.isEmpty()){Genre g=existing.getFirst();if(g.active)return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("genre",view(g),"created",false));
            Genre revived=(Genre)admin.save(user.id(),"genres",g.id,Map.of("name",g.name,"active","true"));return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("genre",view(revived),"created",false,"reactivated",true));}
        Genre genre=(Genre)admin.save(user.id(),"genres",null,Map.of("name",name,"active","true"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("genre",view(genre),"created",true));
    }
    @GetMapping public ResponseEntity<Map<String,Object>> options(@RequestParam String kind,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="")String selected){
        Class<? extends Row> type=switch(kind){case "genres"->Genre.class;case "people"->Person.class;default->throw new AdminFormProblem("Danh mục không hợp lệ.");};
        String entity=db.entityName(type),search=q.trim().toLowerCase(Locale.ROOT);if(search.length()>255)search=search.substring(0,255);
        String where=" where lower(e.name) like :q escape '!'";String pattern="%"+search.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        long total=db.entityManager().createQuery("select count(e) from "+entity+" e"+where,Long.class).setParameter("q",pattern).getSingleResult();int current=AdminPage.number(page,total);
        var rows=db.entityManager().createQuery("from "+entity+" e"+where+" order by e.name,e.id",type).setParameter("q",pattern).setFirstResult(current*5).setMaxResults(5).getResultList();
        Set<Long> ids=MovieCredits.parse(selected,"selection");Map<Long,Row> found=new HashMap<>();if(!ids.isEmpty())db.entityManager().createQuery("from "+entity+" e where e.id in :ids",type).setParameter("ids",ids).getResultList().forEach(r->found.put(r.id,r));
        List<Map<String,Object>> selectedRows=ids.stream().map(id->found.containsKey(id)?view(found.get(id)):Map.<String,Object>of("id",id,"name","Không còn khả dụng · #"+id,"active",false,"available",false)).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("rows",rows.stream().map(this::view).toList(),"selected",selectedRows,"page",current,"total",total,"hasPrevious",current>0,"hasMore",(current+1L)*5<total));
    }
    private Map<String,Object> view(Row row){return row instanceof Genre g?Map.of("id",g.id,"name",g.name,"active",g.active,"available",true):Map.of("id",row.id,"name",((Person)row).name,"active",((Person)row).active,"available",true);}
}
