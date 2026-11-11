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
    public String upload(MultipartFile file,boolean allowVideo)throws IOException{return upload(file,allowVideo?"legacy":"image");}
    public String upload(MultipartFile file,String kind)throws IOException{
        if(file==null||file.isEmpty())throw new IllegalArgumentException("Chọn file để tải lên.");
        if(!Set.of("poster","banner","image","trailer","legacy").contains(kind))throw new IllegalArgumentException("Loại trường upload không hợp lệ.");
        String type=Objects.toString(file.getContentType(),"");String ext;
        if(Set.of("image/jpeg","image/png","image/webp").contains(type)&&!kind.equals("trailer")){
            if(file.getSize()>5*1024*1024)throw new IllegalArgumentException("Ảnh tối đa 5 MB.");
            try(var in=ImageIO.createImageInputStream(file.getInputStream())){
                var readers=ImageIO.getImageReaders(in);if(!readers.hasNext())throw new IllegalArgumentException("Không đọc được ảnh. Chọn JPG, PNG hoặc WEBP hợp lệ.");
                var reader=readers.next();
                try{reader.setInput(in);String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                    ext=switch(format){case "jpeg","jpg"->".jpg";case "png"->".png";case "webp"->".webp";default->throw new IllegalArgumentException("Chỉ nhận ảnh JPG, PNG hoặc WEBP.");};
                    if(reader.getWidth(0)>8000||reader.getHeight(0)>8000)throw new IllegalArgumentException("Ảnh tối đa 8000px mỗi chiều.");
                    if(reader.getNumImages(true)!=1)throw new IllegalArgumentException("Chỉ nhận ảnh tĩnh, không nhận ảnh động.");
                    if(reader.read(0)==null)throw new IllegalArgumentException("Ảnh bị lỗi.");
                }finally{reader.dispose();}
            }catch(javax.imageio.IIOException error){throw new IllegalArgumentException("Ảnh bị lỗi hoặc không đúng định dạng.",error);}
        }else if((kind.equals("trailer")||kind.equals("legacy"))&&type.equals("video/mp4")){
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