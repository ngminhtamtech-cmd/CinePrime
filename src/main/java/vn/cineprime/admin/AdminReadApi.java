package vn.cineprime.admin;

import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** /api/admin/** is guarded by the existing ADMIN security matcher. */
@RestController @RequestMapping("/api/admin")
public class AdminReadApi {
    private final ReportService reports;private final OperationsService operations;private final ShowtimeEditor editor;private final RoomSetup setup;
    public AdminReadApi(ReportService reports,OperationsService operations,ShowtimeEditor editor,RoomSetup setup){this.reports=reports;this.operations=operations;this.editor=editor;this.setup=setup;}
    @GetMapping("/setup-context") public ResponseEntity<Map<String,Object>> context(@RequestParam(required=false)Long cinemaId,@RequestParam(required=false)Long roomId){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(setup.context(cinemaId,roomId));}
    @GetMapping("/rooms/{roomId}/inventory") public ResponseEntity<Map<String,Object>> inventory(@PathVariable Long roomId,@RequestParam Long showtimeId){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(setup.inventory(roomId,showtimeId));}
    @GetMapping("/rooms/{roomId}/shows") public ResponseEntity<Map<String,Object>> shows(@PathVariable Long roomId,@RequestParam(defaultValue="0")int page){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(setup.shows(roomId,page).model());}
    @GetMapping("/rooms/{roomId}/blockers") public ResponseEntity<Map<String,Object>> blockers(@PathVariable Long roomId,@RequestParam(defaultValue="0")int page){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(setup.blockers(roomId,page).model());}
    @GetMapping("/reports/{id}/{section}") public ResponseEntity<Map<String,Object>> report(@PathVariable String id,@PathVariable String section,@RequestParam(defaultValue="0")int page,HttpSession session){
        var snapshot=reports.recall(session,id);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("snapshotId",snapshot.id(),"data",reports.section(snapshot,section,page)));
    }
    @GetMapping("/operations/{section}") public ResponseEntity<Map<String,Object>> operations(@PathVariable String section,@RequestParam(required=false)Long cinemaId,@RequestParam(defaultValue="")String cursor){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(operations.page(section,cinemaId,cursor));
    }
    @GetMapping("/showtime-preview") public ResponseEntity<Map<String,Object>> preview(@RequestParam Long cinemaId,@RequestParam Long roomId,@RequestParam java.time.LocalDate date,@RequestParam(required=false)Long movieId,@RequestParam(required=false)java.time.LocalDateTime startsAt,@RequestParam(required=false)Long editingId,@RequestParam(defaultValue="0")int page){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(editor.preview(cinemaId,roomId,date,movieId,startsAt,editingId,page));}}
