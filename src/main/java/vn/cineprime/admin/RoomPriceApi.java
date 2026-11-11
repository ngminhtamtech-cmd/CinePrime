package vn.cineprime.admin;

import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.cineprime.auth.SecurityConfig.SessionUser;

@RestController @RequestMapping("/api/admin/rooms/{roomId}/prices")
public class RoomPriceApi {
    private final RoomPrices prices;public RoomPriceApi(RoomPrices prices){this.prices=prices;}
    @GetMapping public ResponseEntity<Map<String,Object>> page(@PathVariable Long roomId,@RequestParam(defaultValue="")String seatType,@RequestParam(defaultValue="")String dayType,@RequestParam(defaultValue="0")int page){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(prices.page(roomId,seatType,dayType,page));}
    @PostMapping public Map<String,Object> create(@AuthenticationPrincipal SessionUser user,@PathVariable Long roomId,@RequestBody Map<String,String> fields){return prices.save(user.id(),roomId,null,fields);}
    @PostMapping("/{priceId}") public Map<String,Object> change(@AuthenticationPrincipal SessionUser user,@PathVariable Long roomId,@PathVariable Long priceId,@RequestBody Map<String,String> fields){return prices.save(user.id(),roomId,priceId,fields);}
}
