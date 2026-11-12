package vn.cineprime.admin;

import java.util.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.cineprime.auth.SecurityConfig.SessionUser;
import vn.cineprime.domain.Store;
import vn.cineprime.media.MediaService;
import vn.cineprime.web.BannerService;
import static vn.cineprime.domain.Domain.*;

/** Home hero banners: ordered list, add/edit form, enable/disable, reorder and the auto-rotate interval. */
@Controller @RequestMapping("/admin/banners")
public class BannerController {
    private final BannerService banners;private final Store db;private final AdminController navigation;private final MediaService media;
    public BannerController(BannerService banners,Store db,AdminController navigation,MediaService media){this.banners=banners;this.db=db;this.navigation=navigation;this.media=media;}
    @ModelAttribute public void nav(Model model,HttpServletRequest request){navigation.nav(model,request);model.addAttribute("title","Banner trang chủ · CinePrime Admin");model.addAttribute("resourceLabel","Banner trang chủ");}

    @GetMapping public String list(Model model){model.addAttribute("banners",banners.rows());model.addAttribute("interval",banners.interval());model.addAttribute("minSeconds",BannerService.MIN_SECONDS);model.addAttribute("maxSeconds",BannerService.MAX_SECONDS);return "admin/banners";}
    @GetMapping("/edit") public String edit(@RequestParam(required=false)Long id,Model model){return form(model,banners.form(id),null);}
    String form(Model model,Map<String,Object> banner,String error){model.addAttribute("banner",banner);model.addAttribute("bannerMovies",db.list(Movie.class,"where e.active=true order by e.title,e.id").stream().map(m->Map.<String,Object>of("id",m.id,"title",m.title,"bannerImage",media.bannerOrEmpty(m.banner))).toList());model.addAttribute("formError",error);model.addAttribute("minWidth",MediaService.BANNER_MIN_WIDTH);model.addAttribute("framedWidth",MediaService.FRAMED_MIN_WIDTH);return "admin/banner-form";}

    @PostMapping("/save") public String save(@AuthenticationPrincipal SessionUser user,@RequestParam Map<String,String> values,Model model,HttpServletResponse response,RedirectAttributes redirect){
        try{banners.save(user.id(),values);}
        catch(IllegalArgumentException error){response.setStatus(400);Map<String,Object> kept=new HashMap<>(values);kept.put("active","true".equals(values.get("active")));if(!kept.containsKey("id")||values.get("id").isBlank())kept.put("id",null);return form(model,kept,error.getMessage());}
        redirect.addFlashAttribute("notice","Đã lưu banner.");return "redirect:/admin/banners";
    }
    @PostMapping("/{id}/move") public String move(@AuthenticationPrincipal SessionUser user,@PathVariable Long id,@RequestParam String direction,RedirectAttributes redirect){banners.move(user.id(),id,"up".equals(direction)?-1:1);redirect.addFlashAttribute("notice","Đã đổi thứ tự hiển thị.");return "redirect:/admin/banners";}
    @PostMapping("/{id}/toggle") public String toggle(@AuthenticationPrincipal SessionUser user,@PathVariable Long id,RedirectAttributes redirect){redirect.addFlashAttribute("notice",banners.toggle(user.id(),id)?"Đã bật banner.":"Đã tắt banner.");return "redirect:/admin/banners";}
    @PostMapping("/{id}/delete") public String delete(@AuthenticationPrincipal SessionUser user,@PathVariable Long id,RedirectAttributes redirect){banners.delete(user.id(),id);redirect.addFlashAttribute("notice","Đã xóa banner.");return "redirect:/admin/banners";}
    @PostMapping("/interval") public String interval(@AuthenticationPrincipal SessionUser user,@RequestParam(defaultValue="")String seconds,RedirectAttributes redirect){
        int value;try{value=Integer.parseInt(seconds.trim());}catch(NumberFormatException e){redirect.addFlashAttribute("actionError","Nhập số giây từ "+BannerService.MIN_SECONDS+" đến "+BannerService.MAX_SECONDS+".");return "redirect:/admin/banners";}
        redirect.addFlashAttribute("notice","Banner tự chuyển sau "+banners.interval(user.id(),value)+" giây.");return "redirect:/admin/banners";
    }
}
