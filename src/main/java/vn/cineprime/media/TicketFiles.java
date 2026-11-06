package vn.cineprime.media;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.google.zxing.*;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import vn.cineprime.domain.Store;
import static vn.cineprime.domain.Domain.*;

@Service
public class TicketFiles {
    private final Store db;
    @Value("${cineprime.pdf-font}") String fontPath;
    public TicketFiles(Store db){this.db=db;}
    public byte[] qr(Ticket ticket)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();MatrixToImageWriter.writeToStream(new QRCodeWriter().encode("CP1:"+ticket.token,BarcodeFormat.QR_CODE,320,320),"PNG",out);return out.toByteArray();}
    public byte[] pdf(Ticket ticket)throws Exception{
        Order order=db.get(Order.class,ticket.orderId);Seat seat=db.get(Seat.class,ticket.seatId);
        try(PDDocument doc=new PDDocument();ByteArrayOutputStream out=new ByteArrayOutputStream();InputStream fontStream=fontPath.isBlank()?new ClassPathResource("static/fonts/NotoSans-Regular.ttf").getInputStream():Files.newInputStream(Path.of(fontPath))){
            PDType0Font font=PDType0Font.load(doc,fontStream);PDImageXObject image=PDImageXObject.createFromByteArray(doc,qr(ticket),"qr");
            String[] fields={"VÉ XEM PHIM",order.movieTitle,order.cinemaName+" · "+order.roomName,db.date(order.showStartsAt),"Ghế: "+seat.label,"Mã vé: "+ticket.code,"Mã đơn: "+order.code,"Trạng thái: "+ticket.status};
            List<String> lines=new ArrayList<>();for(String field:fields)lines.addAll(wrap(font,field,532,12));
            for(int offset=0;offset<lines.size();offset+=14){
                PDPage page=new PDPage();doc.addPage(page);
                try(PDPageContentStream canvas=new PDPageContentStream(doc,page)){
                    canvas.setNonStrokingColor(20/255f,89/255f,165/255f);canvas.addRect(0,680,612,112);canvas.fill();
                    canvas.setNonStrokingColor(1f,1f,1f);text(canvas,font,28,40,735,"CinePrime");
                    canvas.setNonStrokingColor(0f,0f,0f);
                    int end=Math.min(offset+14,lines.size()),y=640;
                    for(int i=offset;i<end;i++){text(canvas,font,12,40,y,lines.get(i));y-=20;}
                    if(end==lines.size()){canvas.drawImage(image,40,100,220,220);text(canvas,font,11,40,70,"Mỗi mã QR chỉ được check-in một lần. Vui lòng giữ kín vé.");}
                }
            }
            doc.save(out);return out.toByteArray();
        }
    }
    private List<String> wrap(PDFont font,String raw,float width,int size)throws IOException{
        String value=Objects.toString(raw,"").replaceAll("[\\r\\n\\t]"," ");List<String> lines=new ArrayList<>();StringBuilder line=new StringBuilder();
        for(int codepoint:value.codePoints().toArray()){
            String letter=new String(Character.toChars(codepoint));
            try{font.getStringWidth(letter);}catch(IllegalArgumentException missingGlyph){letter="?";}
            if(font.getStringWidth(line.toString()+letter)/1000*size>width&&!line.isEmpty()){lines.add(line.toString());line.setLength(0);}
            line.append(letter);
        }
        lines.add(line.toString());return lines;
    }
    private void text(PDPageContentStream canvas,PDFont font,int size,int x,int y,String text)throws IOException{canvas.beginText();canvas.setFont(font,size);canvas.newLineAtOffset(x,y);canvas.showText(text.replaceAll("[\\r\\n\\t]"," "));canvas.endText();}
}
