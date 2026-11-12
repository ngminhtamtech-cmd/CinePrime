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
        String webp=media.upload(new MockMultipartFile("file","test.webp","image/webp",webp()),"image");assertTrue(webp.endsWith(".webp"));assertEquals(webp,media.image(webp));assertNotNull(javax.imageio.ImageIO.read(media.read(webp.substring(7)).getFile()));
    }
    static byte[] png(int width,int height)throws IOException{var out=new ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();}
    @Test void bannerUploadsMustBeLandscapeAndWideEnoughForTheHero()throws Exception{
        var media=new MediaService(root.toString());
        for(int[] size:new int[][]{{559,300},{500,300},{1920,2400},{800,700}}){var error=assertThrows(IllegalArgumentException.class,()->media.upload(new MockMultipartFile("file","small.png","image/png",png(size[0],size[1])),"banner"));assertTrue(error.getMessage().contains(size[0]+"×"+size[1]),error.getMessage());}
        String wide=media.upload(new MockMultipartFile("file","wide.png","image/png",png(1920,720)),"banner");assertTrue(media.wide(wide));assertArrayEquals(new int[]{1920,720},media.size(wide));assertEquals("wide",media.bannerMode(wide));assertEquals("",media.bannerWarning(wide));
        String hd=media.upload(new MockMultipartFile("file","hd.png","image/png",png(1920,1080)),"banner");assertTrue(media.wide(hd));
        for(int[] size:new int[][]{{880,348},{599,433},{1599,600},{1600,1000}}){String small=media.upload(new MockMultipartFile("file","small.png","image/png",png(size[0],size[1])),"banner");assertFalse(media.wide(small));assertTrue(media.framed(small));assertEquals("framed",media.bannerMode(small),"Smaller landscape images are kept and shown in a frame");assertTrue(media.bannerWarning(small).contains(size[0]+"×"+size[1]));}
        String poster=media.upload(new MockMultipartFile("file","poster.png","image/png",png(600,900)),"poster");assertFalse(media.wide(poster),"Other kinds keep accepting any size, but are never treated as hero-ready");assertEquals("",media.bannerMode(poster),"A portrait image falls back to the poster slide");
        assertFalse(media.wide("/images/poster-1.svg"));assertFalse(media.wide("/media/00000000-0000-0000-0000-000000000000.png"));assertNull(media.size(""));
    }
    @Test void missingWrongTypeAndOversizedMediaNeverBecomeAnotherMoviePoster()throws Exception{
        var media=new MediaService(root.toString());assertEquals(MediaService.PLACEHOLDER,media.imageOrPlaceholder(""));assertEquals(MediaService.PLACEHOLDER,media.imageOrPlaceholder("/media/00000000-0000-0000-0000-000000000000.png"));assertEquals("",media.bannerOrEmpty("/media/missing.png"));
        assertThrows(IllegalArgumentException.class,()->media.image("https://www.youtube.com/embed/M7lc1UVf-VE"));assertThrows(IllegalArgumentException.class,()->media.image("/images/../application.yml"));
        assertThrows(IllegalArgumentException.class,()->media.upload(new MockMultipartFile("file","fake.png","image/png",new byte[]{1,2,3}),"poster"));
        assertThrows(IllegalArgumentException.class,()->media.upload(new MockMultipartFile("file","large.webp","image/webp",new byte[5*1024*1024+1]),"banner"));
        byte[] video=new byte[]{0,0,0,20,'f','t','y','p','i','s','o','m'};String url=media.upload(new MockMultipartFile("file","trailer.mp4","video/mp4",video),"trailer");assertEquals(url,media.trailer(url));assertThrows(IllegalArgumentException.class,()->media.image(url));assertThrows(IllegalArgumentException.class,()->media.upload(new MockMultipartFile("file","trailer.mp4","video/mp4",video),"poster"));
    }
}
