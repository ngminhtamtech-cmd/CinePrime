package vn.cineprime.media;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {
    private final Path root;
    public MediaService(@Value("${cineprime.uploads}")String dir){root=Path.of(dir).toAbsolutePath().normalize();}
    public String upload(MultipartFile file,boolean allowVideo) throws IOException {
        if(file==null||file.isEmpty())throw new IllegalArgumentException("Chọn file để tải lên.");
        String type=Objects.toString(file.getContentType(),"");String ext;
        if(Set.of("image/jpeg","image/png").contains(type)){
            if(file.getSize()>5*1024*1024)throw new IllegalArgumentException("Ảnh tối đa 5 MB.");
            try(InputStream in=file.getInputStream()){var image=ImageIO.read(in);if(image==null||image.getWidth()>8000||image.getHeight()>8000)throw new IllegalArgumentException("Ảnh không hợp lệ hoặc vượt kích thước 8000px.");}
            ext=type.equals("image/png")?".png":".jpg";
        }else if(allowVideo&&type.equals("video/mp4")){
            if(file.getSize()>100*1024*1024)throw new IllegalArgumentException("Trailer tối đa 100 MB.");
            try(InputStream in=file.getInputStream()){byte[] head=in.readNBytes(12);if(head.length<12||!new String(head,4,4,java.nio.charset.StandardCharsets.US_ASCII).equals("ftyp"))throw new IllegalArgumentException("Trailer MP4 không hợp lệ.");}ext=".mp4";
        }else throw new IllegalArgumentException("Chỉ nhận JPEG, PNG"+(allowVideo?" hoặc MP4.":"."));
        Files.createDirectories(root);String name=UUID.randomUUID()+ext;file.transferTo(root.resolve(name));return "/media/"+name;
    }
    public Resource read(String name){if(!name.matches("[a-f0-9-]{36}\\.(jpg|png|mp4)"))throw new IllegalArgumentException("File không hợp lệ.");Path path=root.resolve(name).normalize();if(!path.startsWith(root)||!Files.isRegularFile(path))throw new IllegalArgumentException("Không tìm thấy file.");return new FileSystemResource(path);}
}
