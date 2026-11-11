package vn.cineprime.admin;

import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.zip.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.cineprime.domain.Store;
import static vn.cineprime.domain.Domain.*;
import static vn.cineprime.admin.ShowtimeBatch.*;

@Service @Transactional(readOnly=true)
public class ShowtimeExcel {
    public static final long MAX_FILE=5*1024*1024;
    public static final String REGISTRATION="Đăng ký suất mới",REFERENCE="Lịch hiện tại",LEGACY="Lịch chiếu";
    private static final String[] HEADERS={"Phim","Phòng","Ngày chiếu","Giờ bắt đầu","Ngôn ngữ","Định dạng","Phụ thu"};
    private static final DateTimeFormatter DISPLAY_DATE=DateTimeFormatter.ofPattern("dd/MM/uuuu");
    public record CellSource(String fileName,String sheetName,int sourceRow,String cell,int token) {}
    public record ImportInfo(int templateVersion,String sheetName,String warning,String weekStart,Map<String,CellSource> cells) {
        public ImportInfo(int version,String sheet,String warning){this(version,sheet,warning,"",Map.of());}
    }
    public record Parsed(Input input,ImportInfo excel) {}
    private final Store db;
    public ShowtimeExcel(Store db){this.db=db;}
    private String movie(Movie m){return "M"+m.id+" — "+m.title;}
    private String room(Room r){return "R"+r.id+" — "+r.name;}

    public static void requireWeek(LocalDate week){
        if(week==null||week.getDayOfWeek()!=DayOfWeek.MONDAY||week.getYear()<1000||week.getYear()>9998)throw new IllegalArgumentException("Chọn tuần bắt đầu vào Thứ Hai, năm từ 1000 đến 9998.");
    }
    public byte[] exportWeek(Long cinemaId,LocalDate week,boolean template){
        requireWeek(week);
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(export(cinemaId,week,week.plusDays(6),template)));var output=new ByteArrayOutputStream()){
            var help=book.getSheet("Hướng dẫn");help.getRow(0).getCell(1).setCellValue("3");
            help.getRow(3).getCell(1).setCellValue(week.format(DISPLAY_DATE)+" – "+week.plusDays(6).format(DISPLAY_DATE));
            help.getRow(6).getCell(1).setCellValue("Một dòng là một phim tại một phòng. Điền giờ vào bảy cột ngày, ví dụ 09:00; 14:30; 19:00. Có 500 dòng trống; tối đa 500 suất mới sau khi tách giờ.");
            help.getRow(7).getCell(1).setCellValue("Lịch hiện tại chỉ để tham khảo. Sao chép giờ cần dùng vào đúng ô ngày của Đăng ký suất mới; không sao chép nguyên dòng vì hai sheet có cột khác nhau.");
            help.getRow(8).getCell(1).setCellValue("Giờ HH:mm (kể cả 11:55), hoặc một ô giờ Excel. Nhiều giờ ngăn bằng dấu ; , hoặc xuống dòng. Chỉ thêm giờ của tuần ghi trên file. Không sửa ngày trên tiêu đề.");
            help.getRow(10).getCell(1).setCellValue("File tối đa 5 MB; cả đợt tối đa 500 suất mới. Số suất tham khảo không tính vào 500. Chỉ tạo suất tương lai; kết thúc và vệ sinh 15 phút do hệ thống tính.");
            help.getRow(12).getCell(1).setCellValue("Import bổ sung vào lịch đang xếp của đúng rạp/tuần. Chọn Nháp hoặc Mở bán trên web; kiểm tra và xác nhận mới ghi lịch.");
            Row metadata=help.createRow(14);metadata.createCell(0).setCellValue("Tuần bắt đầu");metadata.createCell(1).setCellValue(week.toString());
            book.removeSheetAt(book.getSheetIndex(REGISTRATION));var data=book.createSheet(REGISTRATION);book.setSheetOrder(REGISTRATION,2);
            Styles styles=new Styles(book);String[] headers={"Phim","Phòng","","","","","","","","Ngôn ngữ","Định dạng","Phụ thu"};header(data,headers,styles.heading);
            CellStyle dateHeader=book.createCellStyle();dateHeader.cloneStyleFrom(styles.heading);dateHeader.setDataFormat(book.createDataFormat().getFormat("[$-042A]ddd dd/mm/yyyy"));
            for(int d=0;d<7;d++){Cell cell=data.getRow(0).getCell(d+2);cell.setCellValue(week.plusDays(d).atStartOfDay());cell.setCellStyle(dateHeader);}
            CellStyle[] inputs=new CellStyle[12];for(int col=0;col<12;col++){inputs[col]=book.createCellStyle();inputs[col].cloneStyleFrom(styles.input(0));inputs[col].setDataFormat(book.createDataFormat().getFormat(col>=2&&col<=8?"hh:mm":col==11?"#,##0":"General"));data.setColumnWidth(col,(col==0?48:col==1?30:24)*256);}
            for(int n=1;n<=LIMIT;n++){Row row=data.createRow(n);row.setHeightInPoints(36);for(int col=0;col<12;col++)row.createCell(col,CellType.BLANK).setCellStyle(inputs[col]);}
            for(int col=0;col<2;col++){var helper=data.getDataValidationHelper();var validation=helper.createValidation(helper.createFormulaListConstraint(col==0?"DanhSachPhim":"DanhSachPhong"),new CellRangeAddressList(1,LIMIT,col,col));validation.setShowErrorBox(true);validation.createErrorBox("Danh mục không hợp lệ","Chọn mã và tên trong danh mục.");data.addValidationData(validation);}
            data.createFreezePane(2,1);data.setDisplayGridlines(false);data.setAutoFilter(new CellRangeAddress(0,LIMIT,0,11));data.protectSheet("");data.lockAutoFilter(false);data.lockSelectLockedCells(false);data.lockSelectUnlockedCells(false);
            book.setActiveSheet(2);for(int n=0;n<book.getNumberOfSheets();n++)book.getSheetAt(n).setSelected(n==2);data.setActiveCell(new CellAddress("A2"));book.write(output);return output.toByteArray();
        }catch(IOException e){throw new IllegalArgumentException("Không xuất được file Excel tuần.");}
    }

    public byte[] export(Long cinemaId,LocalDate from,LocalDate to,boolean template){
        Cinema cinema=db.get(Cinema.class,cinemaId);
        if(!template&&(from==null||to==null||to.isBefore(from)||java.time.temporal.ChronoUnit.DAYS.between(from,to)>=31))throw new IllegalArgumentException("Khoảng xuất từ 1 đến 31 ngày.");
        var movies=db.list(Movie.class,"order by e.title,e.id");
        var rooms=db.list(Room.class,"where e.cinemaId=?1 order by e.name,e.id",cinemaId);
        Map<Long,Movie> byMovie=new HashMap<>();movies.forEach(m->byMovie.put(m.id,m));
        Map<Long,Room> byRoom=new HashMap<>();rooms.forEach(r->byRoom.put(r.id,r));
        try(XSSFWorkbook book=new XSSFWorkbook();ByteArrayOutputStream output=new ByteArrayOutputStream()){
            var help=book.createSheet("Hướng dẫn");var reference=book.createSheet(REFERENCE);
            var data=book.createSheet(REGISTRATION);var catalog=book.createSheet("Danh mục");
            Styles styles=new Styles(book);
            help(help,cinema,template?"Mẫu trống":from.format(DISPLAY_DATE)+" – "+to.format(DISPLAY_DATE),styles);
            header(data,HEADERS,styles.heading);header(reference,new String[]{"Phim","Phòng","Ngày chiếu","Giờ bắt đầu","Ngôn ngữ","Định dạng","Phụ thu","Kết thúc","Hết vệ sinh","Trạng thái"},styles.heading);
            header(catalog,new String[]{"Phim","Phòng của rạp"},styles.heading);
            for(int n=1;n<=LIMIT;n++){
                Row row=data.createRow(n);row.setHeightInPoints(30);
                for(int col=0;col<7;col++)row.createCell(col,CellType.BLANK).setCellStyle(styles.input(col));
            }
            for(int n=0;n<Math.max(movies.size(),rooms.size());n++){
                Row row=catalog.createRow(n+1);row.setHeightInPoints(24);
                if(n<movies.size())row.createCell(0).setCellValue(movie(movies.get(n)));
                if(n<rooms.size())row.createCell(1).setCellValue(room(rooms.get(n)));
            }
            if(!template){
                var shows=db.query("select s from "+db.entityName(Showtime.class)+" s, "+db.entityName(Room.class)+" r where s.roomId=r.id and r.cinemaId=?1 and s.status in ('DRAFT','OPEN') and s.startsAt>=?2 and s.startsAt<?3 order by s.startsAt,r.name,r.id,s.id",Showtime.class,cinemaId,from.atStartOfDay(ReportService.ZONE).toInstant(),to.plusDays(1).atStartOfDay(ReportService.ZONE).toInstant());
                if(shows.size()>20000)throw new IllegalArgumentException("Lịch quá lớn. Chọn khoảng ngày ngắn hơn.");
                shows.sort(Comparator.comparing((Showtime s)->s.startsAt.atZone(ReportService.ZONE).toLocalDate()).thenComparing(s->byRoom.get(s.roomId).name).thenComparing(s->s.roomId).thenComparing(s->s.startsAt));
                for(Showtime show:shows){
                    Row row=reference.createRow(reference.getLastRowNum()+1);var local=show.startsAt.atZone(ReportService.ZONE);
                    String title=movie(byMovie.get(show.movieId)),roomName=room(byRoom.get(show.roomId));
                    row.setHeightInPoints(Math.max(30,Math.min(84,15*(1+Math.max(title.length()/45,roomName.length()/28)))));
                    for(int col=0;col<10;col++)row.createCell(col).setCellStyle(styles.reference(col));
                    row.getCell(0).setCellValue(title);row.getCell(1).setCellValue(roomName);
                    row.getCell(2).setCellValue(local.toLocalDate().atStartOfDay());row.getCell(3).setCellValue(local.toLocalTime().toSecondOfDay()/86400d);
                    row.getCell(4).setCellValue(show.language);row.getCell(5).setCellValue(show.format);row.getCell(6).setCellValue(show.surcharge);
                    row.getCell(7).setCellValue(show.endsAt.atZone(ReportService.ZONE).toLocalDateTime());
                    row.getCell(8).setCellValue(show.endsAt.plusSeconds(ShowtimeRules.CLEANUP_SECONDS).atZone(ReportService.ZONE).toLocalDateTime());
                    row.getCell(9).setCellValue(show.status.equals("OPEN")?"Mở bán":"Nháp");
                }
            }
            for(int col=0;col<2;col++){
                String name=col==0?"DanhSachPhim":"DanhSachPhong";Name range=book.createName();range.setNameName(name);
                String letter=col==0?"A":"B";range.setRefersToFormula("'Danh mục'!$"+letter+"$2:$"+letter+"$"+Math.max(2,(col==0?movies.size():rooms.size())+1));
                var helper=data.getDataValidationHelper();var validation=helper.createValidation(helper.createFormulaListConstraint(name),new CellRangeAddressList(1,LIMIT,col,col));
                validation.setShowErrorBox(true);validation.createErrorBox("Danh mục không hợp lệ","Chọn mã và tên trong danh mục.");data.addValidationData(validation);
            }
            int[] widths={48,30,18,18,22,18,18,24,24,16};
            for(int col=0;col<widths.length;col++){reference.setColumnWidth(col,widths[col]*256);if(col<7)data.setColumnWidth(col,widths[col]*256);}
            for(var sheet:List.of(data,reference,catalog)){sheet.createFreezePane(0,1);sheet.setDisplayGridlines(false);}
            catalog.setColumnWidth(0,60*256);catalog.setColumnWidth(1,40*256);
            data.setAutoFilter(new CellRangeAddress(0,LIMIT,0,6));reference.setAutoFilter(new CellRangeAddress(0,Math.max(1,reference.getLastRowNum()),0,9));
            reference.protectSheet("");reference.lockAutoFilter(false);reference.lockSelectLockedCells(false);reference.lockSelectUnlockedCells(false);
            book.setActiveSheet(2);for(int n=0;n<book.getNumberOfSheets();n++)book.getSheetAt(n).setSelected(n==2);data.setActiveCell(new CellAddress("A2"));
            book.write(output);return output.toByteArray();
        }catch(IOException e){throw new IllegalArgumentException("Không xuất được file Excel.");}
    }

    private void header(Sheet sheet,String[] labels,CellStyle style){
        Row row=sheet.createRow(0);row.setHeightInPoints(32);
        for(int n=0;n<labels.length;n++){var cell=row.createCell(n);cell.setCellValue(labels[n]);cell.setCellStyle(style);}
    }
    private void help(Sheet sheet,Cinema cinema,String period,Styles styles){
        String[] labels={"CinePrime — đăng ký thêm suất chiếu","Rạp","Tên rạp","Phạm vi lịch tham khảo","Cách sử dụng","Lịch hiện tại","Đăng ký suất mới","Sao chép lịch","Ngày và giờ","Giá trị mặc định","Giới hạn","Danh mục","Xác nhận","Lưu ý"};
        String[] values={"2",cinema.id.toString(),cinema.name,period,"Xuất lịch → điền sheet Đăng ký suất mới → import lên lịch tuần → kiểm tra → xác nhận tạo.","Chỉ tham khảo. Sheet được bảo vệ khỏi sửa nhầm; vẫn chọn và sao chép được. Import không tạo lại hoặc cập nhật các suất tại đây.","Mỗi dòng một suất mới; 500 dòng trống có sẵn định dạng và dropdown. Bỏ qua dòng hoàn toàn trống; dòng điền thiếu được báo lỗi.","Chỉ sao chép bảy cột đầu (Phim đến Phụ thu) sang Đăng ký suất mới, rồi đổi ngày/giờ. Giữ nguyên giờ trùng sẽ bị chặn.","Ngày dd/MM/yyyy, giờ HH:mm hoặc ô ngày/giờ Excel. Backend tính giờ kết thúc và 15 phút vệ sinh. Chỉ tạo suất tương lai theo giờ Việt Nam.","Ngôn ngữ trống: Phụ đề; định dạng trống: theo phòng; phụ thu trống: 0 VND, số nguyên không âm.","File 5 MB; toàn đợt trên web tối đa 500 suất mới. Các suất trong Lịch hiện tại không tính vào giới hạn 500. Xuất tối đa 31 ngày/20.000 suất tham khảo.","Chọn phim/phòng bằng mã — tên. Giữ metadata, tên sheet và tiêu đề cột. Backend xác minh danh mục hiện tại của rạp.","Import bổ sung vào các suất đang tick và file đã nhập. Chọn Nháp/Mở bán trên web; một dòng lỗi chặn cả đợt.","Không nhập công thức hoặc liên kết ngoài. File chưa có dòng đăng ký mới không thay đổi lựa chọn trên web."};
        for(int n=0;n<labels.length;n++){
            Row row=sheet.createRow(n);row.setHeightInPoints(n<4?30:48);
            row.createCell(0).setCellValue(labels[n]);row.getCell(0).setCellStyle(n==0?styles.heading:styles.label);
            row.createCell(1).setCellValue(values[n]);row.getCell(1).setCellStyle(styles.help);
        }
        sheet.setColumnWidth(0,37*256);sheet.setColumnWidth(1,105*256);sheet.setDisplayGridlines(false);sheet.createFreezePane(0,1);
    }

    private static final class Styles {
        final CellStyle heading,label,help;private final CellStyle[] input=new CellStyle[7],reference=new CellStyle[10];
        Styles(XSSFWorkbook book){
            var normal=book.createFont();normal.setFontName("Calibri");normal.setFontHeightInPoints((short)11);
            var bold=book.createFont();bold.setFontName("Calibri");bold.setFontHeightInPoints((short)11);bold.setBold(true);
            var white=book.createFont();white.setFontName("Calibri");white.setFontHeightInPoints((short)11);white.setBold(true);white.setColor(IndexedColors.WHITE.getIndex());
            var blue=book.createFont();blue.setFontName("Calibri");blue.setFontHeightInPoints((short)11);blue.setColor(IndexedColors.BLUE.getIndex());
            var base=book.createCellStyle();base.setFont(normal);base.setVerticalAlignment(VerticalAlignment.CENTER);base.setWrapText(true);
            base.setBorderTop(BorderStyle.THIN);base.setBorderBottom(BorderStyle.THIN);base.setBorderLeft(BorderStyle.THIN);base.setBorderRight(BorderStyle.THIN);
            base.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());base.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());base.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());base.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            var header=book.createCellStyle();header.cloneStyleFrom(base);header.setFont(white);header.setFillForegroundColor(new XSSFColor(new byte[]{0x14,0x59,(byte)0xa5},new DefaultIndexedColorMap()));header.setFillPattern(FillPatternType.SOLID_FOREGROUND);heading=header;
            label=book.createCellStyle();label.cloneStyleFrom(base);label.setFont(bold);
            help=book.createCellStyle();help.cloneStyleFrom(base);
            String[] formats={"General","General","dd/mm/yyyy","hh:mm","General","General","#,##0","dd/mm/yyyy hh:mm","dd/mm/yyyy hh:mm","General"};
            for(int col=0;col<10;col++){
                reference[col]=book.createCellStyle();reference[col].cloneStyleFrom(base);reference[col].setDataFormat(book.createDataFormat().getFormat(formats[col]));
                reference[col].setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());reference[col].setFillPattern(FillPatternType.SOLID_FOREGROUND);
                if(col<7){input[col]=book.createCellStyle();input[col].cloneStyleFrom(base);input[col].setDataFormat(book.createDataFormat().getFormat(formats[col]));input[col].setLocked(false);input[col].setFont(blue);}
            }
        }
        CellStyle input(int col){return input[col];}CellStyle reference(int col){return reference[col];}
    }

    /** Retains the existing service entry point for callers that only need batch input. */
    public Input read(Long cinemaId,String targetStatus,MultipartFile file){return readWorkbook(cinemaId,targetStatus,file).input();}

    public Parsed readWorkbook(Long cinemaId,String targetStatus,MultipartFile file){
        return readWorkbook(cinemaId,targetStatus,file,null);
    }
    public Parsed readWorkbook(Long cinemaId,String targetStatus,MultipartFile file,LocalDate expectedWeek){
        if(expectedWeek!=null)requireWeek(expectedWeek);
        if(file==null||file.isEmpty()||file.getSize()>MAX_FILE||!text(file.getOriginalFilename()).toLowerCase(Locale.ROOT).endsWith(".xlsx"))throw new Failure(413,"INVALID_FILE","Chọn file .xlsx không quá 5 MB.",null);
        try{
            byte[] bytes=file.getBytes();long total=0;int entries=0;
            try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){byte[] buffer=new byte[8192];while(zip.getNextEntry()!=null){if(++entries>100)throw new IOException();int n;while((n=zip.read(buffer))!=-1){total+=n;if(total>32*1024*1024)throw new IOException();}}}
            try(XSSFWorkbook book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){
                if(book.getNumberOfSheets()>8||!book.getExternalLinksTable().isEmpty())throw new IOException();
                Sheet help=book.getSheet("Hướng dẫn");String version=help==null?"":cell(help.getRow(0),1);
                if(!Set.of("1","2","3").contains(version)||cinemaId==null||!cinemaId.toString().equals(cell(help.getRow(1),1)))throw new IllegalArgumentException("File không đúng mẫu hoặc thuộc rạp khác. Tải lại mẫu của rạp đang chọn.");
                String sheetName=version.equals("1")?LEGACY:REGISTRATION;Sheet sheet=book.getSheet(sheetName);
                if(sheet==null)throw new IllegalArgumentException("Thiếu sheet "+sheetName+". Giữ tên sheet của mẫu.");
                if(version.equals("3"))return readWeekly(book,sheet,help,cinemaId,targetStatus,file,expectedWeek);
                for(int col=0;col<HEADERS.length;col++)if(!HEADERS[col].equals(cell(sheet.getRow(0),col)))throw new IllegalArgumentException("Tiêu đề cột trong sheet "+sheetName+" không đúng mẫu.");
                List<Line> lines=new ArrayList<>();if(sheet.getLastRowNum()>20000)throw new IllegalArgumentException("Sheet "+sheetName+" có quá nhiều dòng. Dùng lại mẫu trống.");
                for(int n=1;n<=sheet.getLastRowNum();n++){
                    Row row=sheet.getRow(n);if(row==null)continue;boolean empty=true;
                    for(int col=0;col<row.getLastCellNum();col++)if(!cell(row,col).isBlank())empty=false;
                    if(empty)continue;
                    if(lines.size()>=LIMIT)throw new IllegalArgumentException("Tối đa 500 suất mới mỗi lần nhập. Hãy chia file thành các đợt.");
                    for(int col=7;col<row.getLastCellNum();col++)if(!cell(row,col).isBlank())throw new IllegalArgumentException("Sheet "+sheetName+", dòng "+(n+1)+" có dữ liệu ngoài bảy cột của mẫu.");
                    lines.add(new Line(UUID.randomUUID().toString(),n+1,code(cell(row,0),'M'),code(cell(row,1),'R'),date(row.getCell(2),book),time(row.getCell(3),book),cell(row,4),cell(row,5),cell(row,6)));
                }
                if(lines.isEmpty())throw new Failure(422,"EMPTY_REGISTRATION","Chưa có suất mới trong sheet "+sheetName+".",null);
                String warning=version.equals("1")?"Đây là mẫu phiên bản 1: mọi dòng có dữ liệu trong sheet Lịch chiếu đều được hiểu là suất mới; không tự bỏ qua suất trùng.":"";
                checkWeek(lines,expectedWeek);
                return new Parsed(new Input(cinemaId,"EXCEL",targetStatus,lines),new ImportInfo(Integer.parseInt(version),sheetName,warning));
            }
        }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("File Excel không hợp lệ, quá lớn khi giải nén hoặc không đọc được.");}
    }
    private Parsed readWeekly(XSSFWorkbook book,Sheet sheet,Sheet help,Long cinemaId,String status,MultipartFile file,LocalDate expectedWeek){
        LocalDate week;try{week=LocalDate.parse(cell(help.getRow(14),1));requireWeek(week);}catch(RuntimeException e){throw new IllegalArgumentException("Mẫu v3 thiếu hoặc sai tuần bắt đầu. Tải lại mẫu của tuần đang xếp.");}
        if(expectedWeek!=null&&!expectedWeek.equals(week))throw new IllegalArgumentException("File thuộc tuần "+week.format(DISPLAY_DATE)+" – "+week.plusDays(6).format(DISPLAY_DATE)+". Chọn đúng tuần trên web rồi nhập lại; lịch đang xếp được giữ nguyên.");
        for(int col:new int[]{0,1,9,10,11})if(!HEADERS[col<2?col:col-5].equals(cell(sheet.getRow(0),col)))throw new IllegalArgumentException("Tiêu đề cột trong sheet "+REGISTRATION+" không đúng mẫu v3.");
        for(int d=0;d<7;d++)if(!week.plusDays(d).toString().equals(date(sheet.getRow(0)==null?null:sheet.getRow(0).getCell(d+2),book)))throw new IllegalArgumentException("Bảy cột ngày phải đúng tuần trên file. Không sửa tiêu đề ngày; tải lại mẫu để đổi tuần.");
        if(sheet.getLastRowNum()>20000)throw new IllegalArgumentException("Sheet "+REGISTRATION+" có quá nhiều dòng.");
        List<Line> lines=new ArrayList<>();Map<String,CellSource> sources=new LinkedHashMap<>();
        for(int n=1;n<=sheet.getLastRowNum();n++){
            Row row=sheet.getRow(n);if(row==null)continue;boolean empty=true;for(int col=0;col<row.getLastCellNum();col++)if(!cell(row,col).isBlank())empty=false;if(empty)continue;
            for(int col=12;col<row.getLastCellNum();col++)if(!cell(row,col).isBlank())throw new IllegalArgumentException("Sheet "+REGISTRATION+", dòng "+(n+1)+" có dữ liệu ngoài 12 cột của mẫu.");
            boolean hasHours=false;
            for(int d=0;d<7;d++){
                String value=time(row.getCell(d+2),book);if(value.isBlank())continue;hasHours=true;int token=0;
                for(String raw:value.split("[;,\\r\\n]+")){String hour=raw.trim();if(hour.isEmpty())continue;addWeekly(lines,sources,file,n,d+2,++token,code(cell(row,0),'M'),code(cell(row,1),'R'),week.plusDays(d).toString(),hour,cell(row,9),cell(row,10),cell(row,11));}
                if(token==0)addWeekly(lines,sources,file,n,d+2,1,code(cell(row,0),'M'),code(cell(row,1),'R'),week.plusDays(d).toString(),value,cell(row,9),cell(row,10),cell(row,11));
            }
            if(!hasHours)addWeekly(lines,sources,file,n,2,0,code(cell(row,0),'M'),code(cell(row,1),'R'),"","",cell(row,9),cell(row,10),cell(row,11));
        }
        if(lines.isEmpty())throw new Failure(422,"EMPTY_REGISTRATION","Chưa có suất mới trong sheet "+REGISTRATION+".",null);
        return new Parsed(new Input(cinemaId,"EXCEL",status,lines),new ImportInfo(3,REGISTRATION,"",week.toString(),sources));
    }
    private void addWeekly(List<Line> lines,Map<String,CellSource> sources,MultipartFile file,int row,int col,int token,String movie,String room,String day,String hour,String language,String format,String surcharge){
        if(lines.size()>=LIMIT)throw new IllegalArgumentException("Tối đa 500 suất mới sau khi tách giờ; file chưa được bổ sung.");
        String key=UUID.randomUUID().toString();lines.add(new Line(key,row+1,movie,room,day,hour,language,format,surcharge));sources.put(key,new CellSource(Objects.toString(file.getOriginalFilename(),"Excel"),REGISTRATION,row+1,new CellAddress(row,col).formatAsString(),token));
    }
    private void checkWeek(List<Line> lines,LocalDate week){
        if(week==null)return;
        for(Line line:lines){LocalDate day;try{day=LocalDate.parse(line.date());}catch(RuntimeException e){continue;}if(day.isBefore(week)||day.isAfter(week.plusDays(6)))throw new IllegalArgumentException("Dòng Excel "+line.sourceRow()+" có ngày "+day.format(DISPLAY_DATE)+" ngoài tuần đang xếp. File chưa được bổ sung; sửa ngày hoặc chọn đúng tuần rồi nhập lại.");}
    }
    private String code(String raw,char prefix){var m=java.util.regex.Pattern.compile("^"+prefix+"([1-9][0-9]*)(?:\\s+—.*)?$").matcher(raw);return m.matches()?m.group(1):raw;}
    private String cell(Row row,int column){if(row==null)return "";Cell c=row.getCell(column);if(c==null)return "";if(c.getCellType()==CellType.FORMULA)return "[Công thức không được hỗ trợ]";return switch(c.getCellType()){case NUMERIC->java.math.BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros().toPlainString();case STRING->c.getStringCellValue().trim();case BLANK->"";default->"[Giá trị ô không hợp lệ]";};}
    private String date(Cell c,XSSFWorkbook book){if(c!=null&&c.getCellType()==CellType.NUMERIC){try{return DateUtil.getLocalDateTime(c.getNumericCellValue(),book.isDate1904()).toLocalDate().toString();}catch(RuntimeException ignored){}}String raw=c==null?"":cell(c.getRow(),c.getColumnIndex());try{return LocalDate.parse(raw,DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)).toString();}catch(RuntimeException e){return raw;}}
    private String time(Cell c,XSSFWorkbook book){if(c!=null&&c.getCellType()==CellType.NUMERIC){double value=c.getNumericCellValue();if(value>=0&&value<1){long seconds=Math.round(value*86400);if(seconds<86400&&seconds%60==0)return LocalTime.ofSecondOfDay(seconds).toString();}return "[Giờ Excel không hợp lệ]";}return c==null?"":cell(c.getRow(),c.getColumnIndex());}
}
