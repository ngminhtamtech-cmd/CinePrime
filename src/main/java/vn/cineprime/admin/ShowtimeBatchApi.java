package vn.cineprime.admin;

import java.time.*;
import java.util.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.cineprime.auth.SecurityConfig.SessionUser;
import static vn.cineprime.admin.ShowtimeBatch.*;

@RestController @RequestMapping("/api/admin/showtime-batches")
public class ShowtimeBatchApi {
    private final ShowtimeBatches batches;private final ShowtimeExcel excel;
    public ShowtimeBatchApi(ShowtimeBatches batches,ShowtimeExcel excel){this.batches=batches;this.excel=excel;}
    @ModelAttribute public void noStore(HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping("/calendar") public Map<String,Object> calendar(@RequestParam Long cinemaId,@RequestParam(required=false)Long roomId,@RequestParam(required=false)LocalDate weekStart,@RequestParam(required=false)LocalDate date){
        if(weekStart!=null){if(date!=null)throw new IllegalArgumentException("Chọn tuần hoặc ngày, không chọn cả hai.");return batches.week(cinemaId,roomId,weekStart);}
        if(date==null)throw new IllegalArgumentException("Chọn tuần để xem lịch.");return batches.calendar(cinemaId,date);
    }
    @PostMapping("/preview") public Preview preview(@RequestBody Input input){return batches.preview(input);}
    @PostMapping("/commit") public Result commit(@AuthenticationPrincipal SessionUser user,@RequestBody Commit command){return batches.commit(user.id(),command);}
    @GetMapping("/results/{key}") public ResponseEntity<?> result(@AuthenticationPrincipal SessionUser user,@PathVariable String key){return batches.result(user.id(),key).<ResponseEntity<?>>map(ResponseEntity::ok).orElseGet(()->ResponseEntity.status(202).body(Map.of("code","RESULT_UNKNOWN","message","Chưa xác định được kết quả. Giao dịch có thể vẫn đang xử lý; không tạo một yêu cầu mới.")));}
    @PostMapping("/import-preview") public Map<String,Object> read(@RequestParam Long cinemaId,@RequestParam(defaultValue="DRAFT")String targetStatus,@RequestParam MultipartFile file,@RequestParam(required=false)LocalDate weekStart){var parsed=excel.readWorkbook(cinemaId,targetStatus,file,weekStart);return Map.of("input",parsed.input(),"preview",batches.preview(parsed.input()),"excel",parsed.excel());}
    @GetMapping("/template.xlsx") public ResponseEntity<byte[]> template(@RequestParam Long cinemaId,@RequestParam(required=false)LocalDate weekStart){return download(weekStart==null?excel.export(cinemaId,null,null,true):excel.exportWeek(cinemaId,weekStart,true),"cineprime-mau-suat-chieu.xlsx");}
    @GetMapping("/export.xlsx") public ResponseEntity<byte[]> export(@RequestParam Long cinemaId,@RequestParam(required=false)LocalDate from,@RequestParam(required=false)LocalDate to,@RequestParam(required=false)LocalDate weekStart){if(weekStart!=null&&(from!=null||to!=null))throw new IllegalArgumentException("Chọn tuần hoặc khoảng ngày, không chọn cả hai.");return download(weekStart==null?excel.export(cinemaId,from,to,false):excel.exportWeek(cinemaId,weekStart,false),"cineprime-lich-"+(weekStart==null?from+"-"+to:weekStart)+".xlsx");}
    private ResponseEntity<byte[]> download(byte[] bytes,String name){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(name).build().toString()).body(bytes);}
    @ExceptionHandler(Failure.class) public ResponseEntity<Map<String,Object>> failure(Failure failure){Map<String,Object> body=new LinkedHashMap<>();body.put("code",failure.code);body.put("message",failure.getMessage());if(failure.preview!=null)body.put("preview",failure.preview);return ResponseEntity.status(failure.status).body(body);}
    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class) public ResponseEntity<Map<String,String>> large(){return ResponseEntity.status(413).body(Map.of("message","Chọn file Excel .xlsx không quá 5 MB.","code","INVALID_FILE"));}
}
