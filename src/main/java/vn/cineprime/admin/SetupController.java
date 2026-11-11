package vn.cineprime.admin;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.cineprime.auth.SecurityConfig.SessionUser;
import vn.cineprime.domain.Store;
import static vn.cineprime.domain.Domain.*;

@Controller @RequestMapping("/admin")
public class SetupController {
    private final RoomSetup setup;private final Store db;private final AdminController navigation;
    public SetupController(RoomSetup setup,Store db,AdminController navigation){this.setup=setup;this.db=db;this.navigation=navigation;}
    @ModelAttribute public void nav(Model model,HttpServletRequest request){navigation.nav(model,request);}
    @GetMapping("/setup/rooms/{id}") public String room(@PathVariable Long id,@RequestParam(defaultValue="seats")String step,@RequestParam(defaultValue="0")int blockerPage,@RequestParam(defaultValue="0")int showPage,@RequestParam(required=false)Long showtimeId,Model model){
        if(!Set.of("seats","prices","inventory").contains(step))throw new IllegalArgumentException("Bước thiết lập không hợp lệ.");
        model.addAttribute("seatWorkspace",true);var context=setup.context(null,id);model.addAttribute("setup",context);model.addAttribute("step",step);model.addAttribute("title","Thiết lập "+context.get("roomName"));model.addAttribute("blockers",setup.blockers(id,blockerPage));if(showtimeId!=null){Showtime selected=db.get(Showtime.class,showtimeId);if(!selected.roomId.equals(id))throw new IllegalArgumentException("Suất không thuộc phòng.");model.addAttribute("selectedInventoryShow",Map.of("id",selected.id,"title",db.get(Movie.class,selected.movieId).title+" · "+db.date(selected.startsAt)));}
        long total=db.count(Showtime.class,"where e.roomId=?1",id);int page=AdminPage.number(showPage,total);var shows=db.entityManager().createQuery("select s,m.title from "+db.entityName(Showtime.class)+" s, "+db.entityName(Movie.class)+" m where s.movieId=m.id and s.roomId=?1 order by s.startsAt desc,s.id desc",Object[].class).setParameter(1,id).setFirstResult(page*5).setMaxResults(5).getResultList().stream().map(r->{Showtime s=(Showtime)r[0];return Map.<String,Object>of("id",s.id,"title",r[1]+" · "+db.date(s.startsAt),"status",AdminPresentation.text(s.status));}).toList();model.addAttribute("shows",new AdminPage<>(shows,total,page,page>0,(page+1L)*5<total));return "admin/setup";
    }
    public record LayoutRequest(List<RoomSetup.SeatInput> seats,List<Long> deleteIds,String version){}
    @PostMapping(value="/layout",consumes=MediaType.APPLICATION_JSON_VALUE) @ResponseBody public Map<String,Object> create(@AuthenticationPrincipal SessionUser user,@RequestParam Long roomId,@RequestBody LayoutRequest request){setup.create(user.id(),roomId,request.seats(),request.version());var data=setup.context(null,roomId);return Map.of("message","Đã tạo sơ đồ ghế.","seats",data.get("seats"),"version",data.get("version"));}
    @PostMapping("/layout/{roomId}/seats") @ResponseBody public Map<String,Object> change(@AuthenticationPrincipal SessionUser user,@PathVariable Long roomId,@RequestBody LayoutRequest request){setup.change(user.id(),roomId,request.seats(),request.deleteIds()==null?List.of():request.deleteIds(),request.version());var data=setup.context(null,roomId);return Map.of("message","Đã cập nhật ghế.","seats",data.get("seats"),"version",data.get("version"));}
}
