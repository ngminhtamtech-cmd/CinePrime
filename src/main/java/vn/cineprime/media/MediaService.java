package vn.cineprime.media;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {
    public static final String PLACEHOLDER="/images/no-poster.svg";
    private final Path root;
    public MediaService(@Value("${cineprime.uploads}")String dir){root=Path.of(dir).toAbsolutePath().normalize();}
    public String upload(MultipartFile file,String kind)throws IOException{
        if(file==null||file.isEmpty())throw new IllegalArgumentException("Chọn file để tải lên.");
        if(!Set.of("poster","banner","image","trailer").contains(kind))throw new IllegalArgumentException("Loại trường upload không hợp lệ.");
        String type=Objects.toString(file.getContentType(),"");String ext;
        if(Set.of("image/jpeg","image/png","image/webp").contains(type)&&!kind.equals("trailer")){
            if(file.getSize()>5*1024*1024)throw new IllegalArgumentException("Ảnh tối đa 5 MB.");
            try(var in=ImageIO.createImageInputStream(file.getInputStream())){
                var readers=ImageIO.getImageReaders(in);if(!readers.hasNext())throw new IllegalArgumentException("Không đọc được ảnh. Chọn JPG, PNG hoặc WEBP hợp lệ.");
                var reader=readers.next();
                try{reader.setInput(in);String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                    ext=switch(format){case "jpeg","jpg"->".jpg";case "png"->".png";case "webp"->".webp";default->throw new IllegalArgumentException("Chỉ nhận ảnh JPG, PNG hoặc WEBP.");};
                    if(reader.getWidth(0)>8000||reader.getHeight(0)>8000)throw new IllegalArgumentException("Ảnh tối đa 8000px mỗi chiều.");
                    if(kind.equals("banner")&&!framed(reader.getWidth(0),reader.getHeight(0)))throw new IllegalArgumentException("Ảnh banner cần là ảnh ngang (tỉ lệ từ 4:3), rộng tối thiểu "+FRAMED_MIN_WIDTH+"px; khuyến nghị 1920×640. Ảnh này "+reader.getWidth(0)+"×"+reader.getHeight(0)+"px.");
                    if(reader.getNumImages(true)!=1)throw new IllegalArgumentException("Chỉ nhận ảnh tĩnh, không nhận ảnh động.");
                    if(reader.read(0)==null)throw new IllegalArgumentException("Ảnh bị lỗi.");
                }finally{reader.dispose();}
            }catch(javax.imageio.IIOException error){throw new IllegalArgumentException("Ảnh bị lỗi hoặc không đúng định dạng.",error);}
        }else if(kind.equals("trailer")&&type.equals("video/mp4")){
            if(file.getSize()>100*1024*1024)throw new IllegalArgumentException("Trailer tối đa 100 MB.");
            try(InputStream in=file.getInputStream()){byte[] head=in.readNBytes(12);if(head.length<12||!new String(head,4,4,java.nio.charset.StandardCharsets.US_ASCII).equals("ftyp"))throw new IllegalArgumentException("Trailer MP4 không hợp lệ.");}ext=".mp4";
        }else throw new IllegalArgumentException(kind.equals("trailer")?"Chọn video MP4 hoặc nhập link YouTube.":"Chỉ nhận ảnh JPG, PNG hoặc WEBP, tối đa 5 MB.");
        Files.createDirectories(root);String name=UUID.randomUUID()+ext;file.transferTo(root.resolve(name));return "/media/"+name;
    }
    public Resource read(String name){if(!name.matches("[a-f0-9-]{36}\\.(jpg|png|webp|mp4)"))throw new IllegalArgumentException("File không hợp lệ.");Path path=root.resolve(name).normalize();if(!path.startsWith(root)||!Files.isRegularFile(path))throw new IllegalArgumentException("Không tìm thấy file đã tải lên. Vui lòng chọn lại.");return new FileSystemResource(path);}
    public String image(String value){
        String v=Objects.toString(value,"").trim();if(v.isEmpty())return "";
        if(v.matches("/media/[a-f0-9-]{36}\\.(jpg|png|webp)")){read(v.substring(7));return v;}
        if(v.matches("/images/[a-zA-Z0-9_-]+\\.(svg|jpg|png|webp)")&&new ClassPathResource("static"+v).exists())return v;
        throw new IllegalArgumentException("Ảnh chưa được tải lên hợp lệ. Chọn ảnh từ máy và đợi tải xong trước khi Lưu.");
    }
    public String imageOrPlaceholder(String value){try{String v=image(value);return v.isBlank()?PLACEHOLDER:v;}catch(IllegalArgumentException e){return PLACEHOLDER;}}
    public String bannerOrEmpty(String value){try{return image(value);}catch(IllegalArgumentException e){return "";}}
    /** Hero banners are shown full width; anything narrower than this, or not landscape (16:9 or wider), would be visibly upscaled. */
    public static final int BANNER_MIN_WIDTH=1600;
    public static boolean wide(int width,int height){return width>=BANNER_MIN_WIDTH&&height>0&&width*9L>=height*16L;}
    /** Smaller landscape images are still shown, at their own size inside a frame, never stretched. */
    public static final int FRAMED_MIN_WIDTH=560;
    public static boolean framed(int width,int height){return width>=FRAMED_MIN_WIDTH&&height>0&&width*3L>=height*4L;}
    /** True when a stored image is sharp enough for the full-width hero. Uploaded names are random UUIDs and never rewritten, so sizes are cached. */
    public boolean wide(String url){int[] size=size(url);return size!=null&&wide(size[0],size[1]);}
    public boolean framed(String url){int[] size=size(url);return size!=null&&framed(size[0],size[1]);}
    /** Hero display for a banner image: "wide" fills the hero, "framed" sits at its own size beside the text, "" means use the poster instead. */
    public String bannerMode(String url){int[] size=size(url);return size==null?"":wide(size[0],size[1])?"wide":framed(size[0],size[1])?"framed":"";}
    /** Upload note shown to admins when a banner image is accepted but will not fill the hero. */
    public String bannerWarning(String url){int[] size=size(url);return size==null||wide(size[0],size[1])?"":"Ảnh "+size[0]+"×"+size[1]+"px nhỏ hơn "+BANNER_MIN_WIDTH+"px: banner sẽ hiển thị dạng khung ảnh, không phủ kín. Dùng ảnh 1920×640 để phủ kín.";}
    private final Map<String,int[]> sizes=new java.util.concurrent.ConcurrentHashMap<>();
    public int[] size(String url){
        String v=Objects.toString(url,"");if(!v.matches("/media/[a-f0-9-]{36}\\.(jpg|png|webp)"))return null;
        int[] cached=sizes.get(v);if(cached!=null)return cached.length==2?cached:null;
        int[] found=new int[0];
        try(var in=ImageIO.createImageInputStream(read(v.substring(7)).getFile())){var readers=in==null?null:ImageIO.getImageReaders(in);if(readers!=null&&readers.hasNext()){var reader=readers.next();try{reader.setInput(in);found=new int[]{reader.getWidth(0),reader.getHeight(0)};}finally{reader.dispose();}}}
        catch(IOException|IllegalArgumentException e){return null;}
        sizes.put(v,found);return found.length==2?found:null;
    }
    public String trailer(String value){
        String v=Objects.toString(value,"").trim();if(v.isEmpty())return "";
        if(v.matches("/media/[a-f0-9-]{36}\\.mp4")){read(v.substring(7));return v;}
        return youtube(v);
    }
    public static String youtube(String value){
        try{
            URI uri=URI.create(value.trim());String host=Objects.toString(uri.getHost(),"").toLowerCase(Locale.ROOT);
            if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getUserInfo()!=null||uri.getPort()!=-1)throw new IllegalArgumentException();
            String path=Objects.toString(uri.getPath(),"");String id="";
            if(host.equals("youtu.be"))id=path.startsWith("/")?path.substring(1):"";
            else if(Set.of("youtube.com","www.youtube.com","m.youtube.com","youtube-nocookie.com","www.youtube-nocookie.com").contains(host)){
                if(path.equals("/watch")){for(String part:Objects.toString(uri.getRawQuery(),"").split("&"))if(part.startsWith("v="))id=part.substring(2);}
                else if(path.matches("/(?:embed|shorts)/[A-Za-z0-9_-]{11}"))id=path.substring(path.lastIndexOf('/')+1);
            }
            if(!id.matches("[A-Za-z0-9_-]{11}"))throw new IllegalArgumentException();
            return "https://www.youtube-nocookie.com/embed/"+id;
        }catch(RuntimeException e){throw new IllegalArgumentException("Link trailer không hợp lệ. Dán link YouTube watch, youtu.be, shorts hoặc embed của một video.");}
    }
}