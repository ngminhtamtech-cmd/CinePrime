package vn.cineprime.media;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
public class MediaController {
    private final MediaService media;public MediaController(MediaService media){this.media=media;}
    @GetMapping("/media/{name}") public ResponseEntity<Resource> read(@PathVariable String name){MediaType type=name.endsWith(".mp4")?MediaType.valueOf("video/mp4"):name.endsWith(".png")?MediaType.IMAGE_PNG:MediaType.IMAGE_JPEG;return ResponseEntity.ok().contentType(type).header("X-Content-Type-Options","nosniff").body(media.read(name));}
}
