package vn.cineprime.admin;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cineprime.domain.Store;
import static vn.cineprime.domain.Domain.*;

@Service @Transactional
public class MovieCredits {
    private final Store db;
    public MovieCredits(Store db){this.db=db;}
    public Map<String,Set<Long>> ids(Long movie){
        Map<String,Set<Long>> result=new LinkedHashMap<>();result.put("DIRECTOR",new LinkedHashSet<>());result.put("ACTOR",new LinkedHashSet<>());
        if(movie!=null)for(Credit c:db.list(Credit.class,"where e.movieId=?1 order by e.id",movie))result.get(c.kind).add(c.personId);
        return result;
    }
    public static Set<Long> parse(String value,String field){
        Set<Long> result=new LinkedHashSet<>();
        try{for(String part:Objects.toString(value,"").split(",")){if(part.isBlank())continue;long id=Long.parseLong(part.trim());if(id<=0)throw new NumberFormatException();result.add(id);}}
        catch(NumberFormatException e){throw new AdminFormProblem("Người tham gia không hợp lệ. Hãy chọn lại từ danh mục.",field);}
        if(result.size()>1000)throw new AdminFormProblem("Có quá nhiều lựa chọn.",field);return result;
    }
    public Map<String,Set<Long>> selection(Long movie,Map<String,String> values){
        return values.containsKey("creditsPresent")?Map.of("DIRECTOR",parse(values.get("directorIds"),"directorIds"),"ACTOR",parse(values.get("actorIds"),"actorIds")):ids(movie);
    }
    public void validate(Map<String,Set<Long>> selection){
        Set<Long> selected=new HashSet<>();selection.values().forEach(selected::addAll);Set<Long> found=new HashSet<>();
        if(!selected.isEmpty())db.entityManager().createQuery("select p.id from "+db.entityName(Person.class)+" p where p.id in :ids",Long.class).setParameter("ids",selected).getResultList().forEach(found::add);
        for(var entry:selection.entrySet())if(!found.containsAll(entry.getValue()))throw new AdminFormProblem("Người tham gia đã bị xóa. Làm mới danh mục và chọn lại.",entry.getKey().equals("ACTOR")?"actorIds":"directorIds");
    }
    public void replace(Movie movie,Map<String,Set<Long>> selected){
        Set<String> existing=new HashSet<>();
        for(Credit c:db.list(Credit.class,"where e.movieId=?1",movie.id)){existing.add(c.kind+":"+c.personId);if(!selected.get(c.kind).contains(c.personId))db.delete(c);}
        for(var entry:selected.entrySet())for(Long id:entry.getValue())if(!existing.contains(entry.getKey()+":"+id)){Credit c=new Credit();c.movieId=movie.id;c.personId=id;c.kind=entry.getKey();db.save(c);}
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> selected(Long movie,Map<String,String> values,String kind){
        Set<Long> ids=selection(movie,values==null?Map.of():values).get(kind);if(ids.isEmpty())return List.of();
        Map<Long,Person> people=new HashMap<>();db.entityManager().createQuery("from "+db.entityName(Person.class)+" p where p.id in :ids",Person.class).setParameter("ids",ids).getResultList().forEach(p->people.put(p.id,p));
        return ids.stream().map(id->{Person p=people.get(id);return Map.<String,Object>of("id",id,"name",p==null?"Không còn khả dụng · #"+id:p.name,"active",p!=null&&p.active,"selected",true);}).toList();
    }
}
