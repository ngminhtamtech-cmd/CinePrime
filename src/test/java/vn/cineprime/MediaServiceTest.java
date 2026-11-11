package vn.cineprime;

import java.nio.file.*;
import java.util.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import vn.cineprime.media.MediaService;
import static org.junit.jupiter.api.Assertions.*;

class MediaServiceTest {
    @TempDir Path root;
    static byte[] webp(){return Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");}
    @Test void youtubeUsesExactHostsAndCanonicalVideoId(){
        for(String link:List.of("https://www.youtube.com/watch?v=M7lc1UVf-VE&si=ignored","https://youtu.be/M7lc1UVf-VE?si=ignored","https://m.youtube.com/shorts/M7lc1UVf-VE","https://www.youtube.com/embed/M7lc1UVf-VE","https://www.youtube-nocookie.com/embed/M7lc1UVf-VE"))assertEquals("https://www.youtube-nocookie.com/embed/M7lc1UVf-VE",MediaService.youtube(link));
        for(String link:List.of("https://www.youtube.com.evil.test/embed/M7lc1UVf-VE","https://evil.test/youtu.be/M7lc1UVf-VE","http://youtu.be/M7lc1UVf-VE","https://user@youtu.be/M7lc1UVf-VE","javascript:alert(1)","https://www.youtube.com/watch?v=invalid","https://www.youtube.com/other/embed/M7lc1UVf-VE"))assertThrows(IllegalArgumentException.class,()->MediaService.youtube(link));
    }
    @Test void jpegPngAndWebpDecodeAndReferencesSurviveServiceRestart()throws Exception{
        var media=new MediaService(root.toString());
        for(String format:List.of("png","jpeg")){var out=new ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(4,3,java.awt.image.BufferedImage.TYPE_INT_RGB),format,out);String url=media.upload(new MockMultipartFile("file","test."+format,"image/"+format,out.toByteArray()),"poster");assertEquals(url,media.image(url));assertEquals(url,new MediaService(root.toString()).image(url));assertThrows(IllegalArgumentException.class,()->media.trailer(url));}
        String webp=media.upload(new MockMultipartFile("file","test.webp","image/webp",webp()),"banner");assertTrue(webp.endsWith(".webp"));assertEquals(webp,media.image(webp));assertNotNull(javax.imageio.ImageIO.read(media.read(webp.substring(7)).getFile()));
    }
    @Test void missingWrongTypeAndOversizedMediaNeverBecomeAnotherMoviePoster()throws Exception{
        var media=new MediaService(root.toString());assertEquals(MediaService.PLACEHOLDER,media.imageOrPlaceholder(""));assertEquals(MediaService.PLACEHOLDER,media.imageOrPlaceholder("/media/00000000-0000-0000-0000-000000000000.png"));assertEquals("",media.bannerOrEmpty("/media/missing.png"));
        assertThrows(IllegalArgumentException.class,()->media.image("https://www.youtube.com/embed/M7lc1UVf-VE"));assertThrows(IllegalArgumentException.class,()->media.image("/images/../application.yml"));
        assertThrows(IllegalArgumentException.class,()->media.upload(new MockMultipartFile("file","fake.png","image/png",new byte[]{1,2,3}),"poster"));
        assertThrows(IllegalArgumentException.class,()->media.upload(new MockMultipartFile("file","large.webp","image/webp",new byte[5*1024*1024+1]),"banner"));
        byte[] video=new byte[]{0,0,0,20,'f','t','y','p','i','s','o','m'};String url=media.upload(new MockMultipartFile("file","trailer.mp4","video/mp4",video),"trailer");assertEquals(url,media.trailer(url));assertThrows(IllegalArgumentException.class,()->media.image(url));assertThrows(IllegalArgumentException.class,()->media.upload(new MockMultipartFile("file","trailer.mp4","video/mp4",video),"poster"));
    }
}
