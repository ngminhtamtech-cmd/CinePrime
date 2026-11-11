package vn.cineprime.admin;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cineprime.domain.Store;
import static vn.cineprime.domain.Domain.*;

/** One lock order for every catalog writer: genres by ID, then movies/links. */
@Service @Transactional
public class MovieGenres {
    private final Store db;
    public MovieGenres(Store db){this.db=db;}
    public void lockCatalog(){for(Genre g:db.list(Genre.class,"order by e.id"))db.lock(Genre.class,g.id);}
    public Set<Long> ids(Long movie){return movie==null?Set.of():new LinkedHashSet<>(db.query("select e.genreId from "+db.entityName(MovieGenre.class)+" e where e.movieId=?1",Long.class,movie));}
    public static Set<Long> parse(String value){Set<Long> ids=new LinkedHashSet<>();for(String part:Objects.toString(value,"").split(",")){if(part.isBlank())continue;try{ids.add(Long.valueOf(part.trim()));}catch(NumberFormatException e){throw new IllegalArgumentException("Thể loại không hợp lệ.");}}return ids;}
    public Set<Long> selection(Movie m,Map<String,String> values){return values.containsKey("genresPresent")?parse(values.get("genreIds")):ids(m.id);}
    public void validate(Movie m,Set<Long> selected){
        Set<Long> existing=ids(m.id);int active=0;
        for(Long id:selected){Genre g=db.first(Genre.class,"where e.id=?1",id).orElseThrow(()->new IllegalArgumentException("Thể loại không còn tồn tại. Làm mới danh mục và chọn lại."));if(g.active)active++;else if(!existing.contains(id))throw new AdminFormProblem("Không thể gán mới thể loại đã ngừng: "+g.name,"genreIds");}
        if(m.active&&active==0)throw new AdminFormProblem("Chọn ít nhất một thể loại đang hoạt động trước khi hiển thị phim.","genreIds");
    }
    public void requireVisible(Movie m){validate(m,ids(m.id));}
    public void replace(Movie m,Set<Long> selected){
        var links=db.list(MovieGenre.class,"where e.movieId=?1",m.id);Set<Long> before=new HashSet<>();
        for(MovieGenre link:links){before.add(link.genreId);if(!selected.contains(link.genreId))db.delete(link);}
        for(Long id:selected)if(!before.contains(id)){MovieGenre link=new MovieGenre();link.movieId=m.id;link.genreId=id;db.save(link);}
    }
    public void beforeDeactivate(Genre genre){
        String hql="select m from "+db.entityName(Movie.class)+" m where m.active=true and exists (select x.id from "+db.entityName(MovieGenre.class)+" x where x.movieId=m.id and x.genreId=?1) and not exists (select x.id from "+db.entityName(MovieGenre.class)+" x, "+db.entityName(Genre.class)+" g where x.genreId=g.id and x.movieId=m.id and g.active=true and g.id<>?1) order by m.id";
        var affected=db.query(hql,Movie.class,genre.id);
        if(!affected.isEmpty())throw new IllegalArgumentException("Chưa thể ngừng thể loại. Gán thể loại thay thế hoặc ẩn phim trước: "+String.join(", ",affected.stream().limit(5).map(m->m.title+" (#"+m.id+")").toList())+". Mở Sửa thể loại để xem các phim liên quan.");
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> options(Long movie,Set<Long> selection){
        return db.list(Genre.class,"order by e.name,e.id").stream().map(g->Map.<String,Object>of("id",g.id,"name",g.name,"active",g.active,"selected",selection.contains(g.id))).toList();
    }
    @Transactional(readOnly=true) public AdminPage<Map<String,Object>> related(Long genre,int requested){
        String from=" from "+db.entityName(Movie.class)+" m where exists (select x.id from "+db.entityName(MovieGenre.class)+" x where x.movieId=m.id and x.genreId=?1)";
        long total=db.query("select count(m)"+from,Long.class,genre).getFirst();int page=AdminPage.number(requested,total);
        var rows=db.entityManager().createQuery("select m"+from+" order by m.id",Movie.class).setParameter(1,genre).setFirstResult(page*5).setMaxResults(5).getResultList().stream().map(m->Map.<String,Object>of("id",m.id,"title",m.title,"visible",m.active)).toList();return new AdminPage<>(rows,total,page,page>0,(page+1L)*5<total);
    }
}
