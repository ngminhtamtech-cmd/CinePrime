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
    private final Store db;
    public FormOptions(Store db){this.db=db;}
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
