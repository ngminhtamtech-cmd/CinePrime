package vn.cineprime;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import de.neuland.pug4j.PugConfiguration;
import de.neuland.pug4j.template.FileTemplateLoader;
import vn.cineprime.admin.*;
import vn.cineprime.domain.Store;

class AdminFormsTest {
    @Test void allAdminFormsRenderUtf8AndKeepNativePayloadWithoutJavaScript()throws Exception{
        Store db=new Store(){@Override public <T> List<T> list(Class<T> type,String where,Object... args){return List.of();}};
        AdminService service=new AdminService(db,null,null,null,null,null,null,null,null);AdminPresentation presentation=new AdminPresentation(db,service,java.time.Clock.systemUTC());
        PugConfiguration pug=new PugConfiguration();pug.setTemplateLoader(new FileTemplateLoader(Path.of("src/main/resources/templates"),java.nio.charset.StandardCharsets.UTF_8));
        for(String key:AdminService.RESOURCES.keySet()){
            Map<String,Object> model=new HashMap<>();model.put("title","Kiểm thử form");model.put("adminForm",true);model.put("key",key);model.put("resourceLabel",service.resource(key).label());model.put("returnTo","/admin/manage/"+key);model.put("csrfName","_csrf");model.put("csrfToken","test-csrf");model.put("csrfHeader","X-CSRF-TOKEN");model.put("operationsRole","Quản trị");model.put("viewer",Map.of("id",5,"name","Quản trị kiểm thử"));
            if(key.equals("showtimes"))model.put("rowId",1L); // This generic template is retained only for existing showtimes.
            model.put("navItems",List.of(Map.of("id","movies","label","Phim","href","/admin/manage/movies","active",true,"sectionStart","Vận hành")));
            model.put("groups",presentation.groups(key,null,null));model.put("movieGenres",List.of(Map.of("id",1,"name","Phiêu lưu","selected",true,"active",true)));model.put("movieDirectors",List.of(Map.of("id",2,"name","Đạo diễn thử","selected",true,"active",true)));model.put("movieActors",List.of(Map.of("id",3,"name","Diễn viên thử","selected",true,"active",true)));
            model.put("editorMovies",List.of(Map.of("id",1,"title","Phim thử")));model.put("adminCinemas",List.of(Map.of("id",1,"name","Rạp thử")));model.put("editorAllRooms",List.of(Map.of("id",1,"cinemaId",1,"name","Phòng 1")));model.put("cinemaId","1");model.put("roomId","1");model.put("movieId","1");model.put("editorDate","2026-12-01");model.put("editorTime","17:00");model.put("editorStartsAt","2026-12-01T17:00");
            String html;
            if(key.equals("movies")){
                model.put("mv",presentation.values(key,null,null));model.put("movieToday","2026-12-01");model.put("movieShows",Map.of("count",0,"first","","last","","upcoming",0L));
                html=pug.renderTemplate(pug.getTemplate("admin/movie-form"),model);String text=org.springframework.web.util.HtmlUtils.htmlUnescape(html);
                assertTrue(html.contains("name=\"_csrf\""));assertTrue(html.contains("/js/admin-movie.js"));assertTrue(html.contains("name=\"creditsPresent\""),"credits marker");assertTrue(html.contains("name=\"directorIds\""),"director payload");assertTrue(text.contains("Đạo diễn thử"),"director name");assertTrue(text.contains("Diễn viên thử"),"actor name");
                for(String name:List.of("title","slug","description","poster","banner","trailer","ageRating","duration","releaseDate","endDate","active","featured","genreIds"))assertTrue(html.contains("name=\""+name+"\""),name);
                assertTrue(text.contains("Ngày kết thúc chiếu"));assertTrue(html.contains("value=\"P\" checked"),"default age rating");
            }else{html=pug.renderTemplate(pug.getTemplate("admin/edit"),model);assertTrue(html.contains("name=\"_csrf\""),key);assertTrue(html.contains("data-admin-form=\""+key+"\""),key);assertTrue(html.contains("/js/admin-forms.js"),key);}
            Files.createDirectories(Path.of("target/form-fixtures"));Files.writeString(Path.of("target/form-fixtures/"+key+".html"),html);
        }
    }
    @SuppressWarnings("unchecked")
    @Test void popupFormsAndBannerEditorRenderWithTheSamePayloadNames()throws Exception{
        Store db=new Store(){@Override public <T> List<T> list(Class<T> type,String where,Object... args){return List.of();}};
        AdminService service=new AdminService(db,null,null,null,null,null,null,null,null);AdminPresentation presentation=new AdminPresentation(db,service,java.time.Clock.systemUTC());
        PugConfiguration pug=new PugConfiguration();pug.setTemplateLoader(new FileTemplateLoader(Path.of("src/main/resources/templates"),java.nio.charset.StandardCharsets.UTF_8));
        for(String key:List.of("genres","people","movie-genres","credits","contents","users")){
            Map<String,Object> model=new HashMap<>();model.put("key",key);model.put("resourceLabel",service.resource(key).label());model.put("returnTo","/admin/manage/"+key);model.put("csrfName","_csrf");model.put("csrfToken","test-csrf");model.put("required",AdminPresentation.required(key));
            model.put("fields",presentation.groups(key,null,null).stream().flatMap(g->((List<Map<String,Object>>)g.get("fields")).stream()).toList());
            String html=pug.renderTemplate(pug.getTemplate("admin/form-dialog"),model),text=org.springframework.web.util.HtmlUtils.htmlUnescape(html);
            assertTrue(html.contains("name=\"_csrf\""),key);assertTrue(html.contains("action=\"/admin/manage/"+key+"/save\""),key);assertTrue(text.contains("Thêm · "),key);
            for(String name:service.resource(key).fields().split(","))assertTrue(html.contains("name=\""+name+"\""),key+" "+name);
            for(String name:AdminPresentation.required(key))assertTrue(html.matches("(?s).*name=\""+name+"\"[^>]*required.*")||html.matches("(?s).*required[^>]*name=\""+name+"\".*"),key+" required "+name);
            if(key.equals("contents"))assertTrue(html.contains("dialog-grid"),"Article pop-up uses two columns");
            Files.createDirectories(Path.of("target/form-fixtures"));Files.writeString(Path.of("target/form-fixtures/dialog-"+key+".html"),html);
        }
        Map<String,Object> model=new HashMap<>();model.put("title","Banner");model.put("csrfName","_csrf");model.put("csrfToken","test-csrf");model.put("csrfHeader","X-CSRF-TOKEN");model.put("operationsRole","Quản trị");model.put("viewer",Map.of("id",5,"name","Quản trị kiểm thử"));model.put("resourceLabel","Banner trang chủ");
        model.put("navItems",List.of(Map.of("id","content","label","Nội dung","href","/admin/banners","active",true,"sectionStart","Quản lý")));
        model.put("banner",Map.of("kind","MOVIE","movieId","2","title","","image","","link","","active",true,"startsAt","","endsAt",""));model.put("bannerMovies",List.of(Map.of("id",2,"title","Phim thử","bannerImage","/media/x.jpg")));model.put("minWidth",1600);model.put("framedWidth",560);
        String html=pug.renderTemplate(pug.getTemplate("admin/banner-form"),model),text=org.springframework.web.util.HtmlUtils.htmlUnescape(html);
        for(String name:List.of("kind","movieId","title","image","link","startsAt","endsAt","active","_csrf"))assertTrue(html.contains("name=\""+name+"\""),name);
        assertTrue(html.contains("/js/admin-banner.js"));assertTrue(html.contains("data-image=\"/media/x.jpg\""));assertTrue(text.contains("Xem trước trên trang chủ"));assertTrue(html.contains("value=\"MOVIE\" checked"),"Saved kind stays selected");
        Files.writeString(Path.of("target/form-fixtures/banner-form.html"),html);
    }
    @Test void relationIdsRejectMalformedAndDeduplicateWithinEachRole(){assertEquals(Set.of(1L,2L),MovieCredits.parse("1,2,1","actorIds"));assertEquals(Set.of(),MovieCredits.parse(null,"directorIds"));var problem=assertThrows(AdminFormProblem.class,()->MovieCredits.parse("0","actorIds"));assertEquals(List.of("actorIds"),problem.fields());assertThrows(AdminFormProblem.class,()->MovieCredits.parse("1,x","directorIds"));}
    @Test void weekTableRendersAddDialogDayBoardAndFinalConfirmation()throws Exception{
        var pug=new PugConfiguration();pug.setTemplateLoader(new FileTemplateLoader(Path.of("src/main/resources/templates"),java.nio.charset.StandardCharsets.UTF_8));
        Map<String,Object> model=new HashMap<>();model.put("title","Thêm suất chiếu");model.put("resourceLabel","Thêm suất chiếu");model.put("viewer",Map.of("id",5,"name","Quản trị"));model.put("operationsRole","Quản trị");model.put("navItems",List.of());model.put("csrfName","_csrf");model.put("csrfToken","test");model.put("csrfHeader","X-CSRF-TOKEN");model.put("batchCinemaId","1");model.put("batchRoomId","1");model.put("batchMovieId","1");model.put("batchDate","2026-12-07");model.put("adminCinemas",List.of(Map.of("id",1,"name","Rạp tiếng Việt")));model.put("batchRooms",List.of(Map.of("id",1,"name","Phòng 1","format","2D")));model.put("batchMovies",List.of(Map.of("id",1,"title","Phim tiếng Việt","duration",100,"poster","/images/no-poster.svg","status","NOW_SHOWING","active",true)));
        String html=pug.renderTemplate(pug.getTemplate("admin/showtime-batch"),model),decoded=org.springframework.web.util.HtmlUtils.htmlUnescape(html);assertTrue(html.contains("id=\"batch-week\""));assertTrue(html.contains("id=\"batch-add-dialog\""));assertTrue(decoded.contains("Nhiều suất nối tiếp"));assertTrue(decoded.contains("Cả tuần"));assertTrue(decoded.contains("<strong>+ Thêm</strong>"));assertFalse(html.contains("id=\"batch-fill\""));assertTrue(decoded.contains("Chép ngày"));assertTrue(html.contains("id=\"batch-board\""));assertTrue(html.contains("id=\"batch-days\""));assertFalse(html.contains("id=\"batch-open\""));assertTrue(decoded.contains("Tuần trước"));assertTrue(decoded.contains("Phim tiếng Việt"));assertFalse(html.contains("id=\"batch-room-filter\""));assertTrue(html.contains("id=\"batch-room\""));assertTrue(html.contains("id=\"batch-review\""));assertTrue(html.contains("id=\"batch-confirm\""));assertTrue(decoded.contains("Bán vé ngay sau khi tạo"));Files.writeString(Path.of("target/week-table-fixture.html"),html);
    }
}
